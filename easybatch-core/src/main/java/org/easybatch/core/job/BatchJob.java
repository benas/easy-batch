/**
 * The MIT License
 *
 *   Copyright (c) 2017, Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy
 *   of this software and associated documentation files (the "Software"), to deal
 *   in the Software without restriction, including without limitation the rights
 *   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *   copies of the Software, and to permit persons to whom the Software is
 *   furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *   THE SOFTWARE.
 */
package org.easybatch.core.job;

import org.easybatch.core.listener.*;
import org.easybatch.core.processor.RecordProcessor;
import org.easybatch.core.reader.RecordReader;
import org.easybatch.core.record.Batch;
import org.easybatch.core.record.Record;
import org.easybatch.core.writer.RecordWriter;

import java.util.logging.Level;
import java.util.logging.Logger;

import static org.easybatch.core.job.JobStatus.*;
import static org.easybatch.core.util.Utils.formatErrorThreshold;

/**
 * Implementation of read-process-write job pattern.
 *
 * @author Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 */
class BatchJob implements Job {

    private static final Logger LOGGER = Logger.getLogger(BatchJob.class.getName());

    private final String name;

    private final RecordReader recordReader;
    private final RecordWriter recordWriter;
    private final RecordProcessor recordProcessor;
    private final RecordTracker recordTracker;

    private final JobListener jobListener;
    private final BatchListener batchListener;
    private final RecordReaderListener recordReaderListener;
    private final RecordWriterListener recordWriterListener;
    private final PipelineListener pipelineListener;

    private final JobParameters parameters;
    private final JobMetrics metrics;
    private final JobReport report;
    private final JobMonitor monitor;

    BatchJob(JobBuilder builder) {
        this.recordReader = builder.recordReader;
        this.recordWriter = builder.recordWriter;
        this.recordProcessor = builder.recordProcessor;
        this.jobListener = builder.jobListener;
        this.batchListener = builder.batchListener;
        this.recordReaderListener = builder.recordReaderListener;
        this.recordWriterListener = builder.recordWriterListener;
        this.pipelineListener = builder.pipelineListener;
        this.parameters = builder.parameters;
        this.name = builder.name;


        this.recordTracker = new RecordTracker();
        this.metrics = new JobMetrics();
        this.report = new JobReport();
        report.setParameters(parameters);
        report.setMetrics(metrics);
        report.setJobName(name);
        report.setSystemProperties(System.getProperties());
        this.monitor = new JobMonitor(report);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public JobReport call() {
        start();
        try {
            openReader();
            openWriter();
            setStatus(STARTED);
            while (moreRecords() && !isInterrupted()) {
                Batch batch = readAndProcessBatch();
                writeBatch(batch);
            }
            setStatus(STOPPING);
        } catch (Exception exception) {
            fail(exception);
            return report;
        } finally {
            closeReader();
            closeWriter();
        }
        teardown();
        return report;
    }

    /*
     * private methods
     */

    private void start() {
        setStatus(STARTING);
        jobListener.beforeJobStart(parameters);
        metrics.setStartTime(System.currentTimeMillis());
        LOGGER.log(Level.INFO, "Batch size: {0}", parameters.getBatchSize());
        LOGGER.log(Level.INFO, "Error threshold: {0}", formatErrorThreshold(parameters.getErrorThreshold()));
        LOGGER.log(Level.INFO, "Jmx monitoring: {0}", parameters.isJmxMonitoring());
        registerJobMonitor();
    }

    private void registerJobMonitor() {
        if (parameters.isJmxMonitoring()) {
            monitor.registerJmxMBeanFor(this);
        }
    }

    private void openReader() throws RecordReaderOpeningException {
        try {
            LOGGER.log(Level.FINE, "Opening record reader");
            recordReader.open();
        } catch (Exception e) {
            throw new RecordReaderOpeningException("Unable to open record reader", e);
        }
    }

    private void openWriter() throws RecordWriterOpeningException {
        try {
            LOGGER.log(Level.FINE, "Opening record writer");
            recordWriter.open();
        } catch (Exception e) {
            throw new RecordWriterOpeningException("Unable to open record writer", e);
        }
    }

    private void setStatus(JobStatus status) {
        if(isInterrupted()) {
            LOGGER.log(Level.INFO, "Job ''{0}'' has been interrupted, aborting execution.", name);
        }
        LOGGER.log(Level.INFO, "Job ''{0}'' " + status.name().toLowerCase(), name);
        report.setStatus(status);
    }

    private boolean moreRecords() {
        return recordTracker.moreRecords();
    }

    private Batch readAndProcessBatch() throws RecordReadingException, ErrorThresholdExceededException {
        Batch batch = new Batch();
        batchListener.beforeBatchReading();
        for (int i = 0; i < parameters.getBatchSize(); i++) {
            Record record = readRecord();
            if (record == null) {
                recordTracker.noMoreRecords();
                break;
            } else {
                metrics.incrementReadCount();
            }
            processRecord(record, batch);
        }
        batchListener.afterBatchProcessing(batch);
        return batch;
    }

    private Record readRecord() throws RecordReadingException {
        Record record;
        try {
            LOGGER.log(Level.FINE, "Reading next record");
            recordReaderListener.beforeRecordReading();
            record = recordReader.readRecord();
            recordReaderListener.afterRecordReading(record);
            return record;
        } catch (Exception e) {
            recordReaderListener.onRecordReadingException(e);
            throw new RecordReadingException("Unable to read next record", e);
        }
    }

    @SuppressWarnings(value = "unchecked")
    private void processRecord(Record record, Batch batch) throws ErrorThresholdExceededException {
        Record processedRecord = null;
        try {
            LOGGER.log(Level.FINE, "Processing {0}", record);
            notifyJobUpdate();
            Record preProcessedRecord = pipelineListener.beforeRecordProcessing(record);
            if (preProcessedRecord == null) {
                LOGGER.log(Level.FINE, "{0} has been filtered", record);
                metrics.incrementFilterCount();
            } else {
                processedRecord = recordProcessor.processRecord(preProcessedRecord);
                if (processedRecord == null) {
                    LOGGER.log(Level.FINE, "{0} has been filtered", record);
                    metrics.incrementFilterCount();
                } else {
                    batch.addRecord(processedRecord);
                }
            }
            pipelineListener.afterRecordProcessing(record, processedRecord);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unable to process " + record, e);
            pipelineListener.onRecordProcessingException(record, e);
            metrics.incrementErrorCount();
            report.setLastError(e);
            if (metrics.getErrorCount() > parameters.getErrorThreshold()) {
                throw new ErrorThresholdExceededException("Error threshold exceeded. Aborting execution", e);
            }
        }
    }

    private void writeBatch(Batch batch) throws BatchWritingException {
        LOGGER.log(Level.FINE, "Writing {0}", batch);
        try {
            if (!batch.isEmpty()) {
                recordWriterListener.beforeRecordWriting(batch);
                recordWriter.writeRecords(batch);
                recordWriterListener.afterRecordWriting(batch);
                batchListener.afterBatchWriting(batch);
                metrics.incrementWriteCount(batch.size());
            }
        } catch (Exception e) {
            recordWriterListener.onRecordWritingException(batch, e);
            batchListener.onBatchWritingException(batch, e);
            throw new BatchWritingException("Unable to write records", e);
        }
    }

    private boolean isInterrupted() {
        return Thread.currentThread().isInterrupted();
    }

    private void teardown() {
        JobStatus jobStatus = isInterrupted() ? ABORTED : COMPLETED;
        teardown(jobStatus);
    }

    private void teardown(JobStatus status) {
        report.setStatus(status);
        metrics.setEndTime(System.currentTimeMillis());
        LOGGER.log(Level.INFO, "Job ''{0}'' finished with status: {1}", new Object[]{name, report.getStatus()});
        notifyJobUpdate();
        jobListener.afterJobEnd(report);
    }

    private void fail(Exception exception) {
        String reason = exception.getMessage();
        Throwable error = exception.getCause();
        LOGGER.log(Level.SEVERE, reason, error);
        report.setLastError(error);
        teardown(FAILED);
    }

    private void closeReader() {
        try {
            LOGGER.log(Level.FINE, "Closing record reader");
            recordReader.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to close record reader", e);
            report.setLastError(e);
        }
    }

    private void closeWriter() {
        try {
            LOGGER.log(Level.FINE, "Closing record writer");
            recordWriter.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to close record writer", e);
            report.setLastError(e);
        }
    }

    private void notifyJobUpdate() {
        if (parameters.isJmxMonitoring()) {
            monitor.notifyJobReportUpdate();
        }
    }
}
