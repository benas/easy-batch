/*
 * The MIT License
 *
 *   Copyright (c) 2021, Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
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
package org.jeasy.batch.extensions.opencsv;

import com.opencsv.CSVWriter;
import org.jeasy.batch.core.field.BeanFieldExtractor;
import org.jeasy.batch.core.field.FieldExtractor;
import org.jeasy.batch.core.marshaller.RecordMarshaller;
import org.jeasy.batch.core.record.Record;
import org.jeasy.batch.core.record.StringRecord;
import org.jeasy.batch.core.util.Utils;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Marshals a POJO to CSV format using <a href="http://opencsv.sourceforge.net">Open CSV</a>.
 *
 * <strong>This marshaller does not support recursive marshalling.</strong>
 *
 * @author Mahmoud Ben Hassine (mahmoud.benhassine@icloud.com)
 * @param <P> type of the record's payload
 */
public class OpenCsvRecordMarshaller<P> implements RecordMarshaller<P, String> {

    public static final char DEFAULT_DELIMITER = ',';
    public static final char DEFAULT_QUALIFIER = '\"';

    private char delimiter;
    private char qualifier;
    private FieldExtractor<P> fieldExtractor;

    /**
     * Create a new {@link OpenCsvRecordMarshaller}.
     *
     * @param type   the type of object to marshal
     * @param fields the list of fields to marshal in order
     */
    public OpenCsvRecordMarshaller(final Class<P> type, final String... fields) {
        this(new BeanFieldExtractor<>(type, fields));
    }

    /**
     * Create a new {@link OpenCsvRecordMarshaller}.
     *
     * @param fieldExtractor to use to extract fields
     */
    public OpenCsvRecordMarshaller(final FieldExtractor<P> fieldExtractor) {
        Utils.checkNotNull(fieldExtractor, "field extractor");
        this.fieldExtractor = fieldExtractor;
        this.delimiter = DEFAULT_DELIMITER;
        this.qualifier = DEFAULT_QUALIFIER;
    }

    @Override
    public StringRecord processRecord(Record<P> record) throws Exception {
        try (StringWriter stringWriter = new StringWriter();
             CSVWriter csvWriter = new CSVWriter(stringWriter, delimiter, qualifier, "")) {
            // force lineEnd to empty string
            P payload = record.getPayload();
            List<String> fields = extractFields(payload);
            String[] items = fields.toArray(new String[0]);
            csvWriter.writeNext(items);
            csvWriter.flush();
            return new StringRecord(record.getHeader(), stringWriter.toString());
        }
    }

    private List<String> extractFields(P payload) throws Exception {
        List<String> tokens = new ArrayList<>();
        Iterable<Object> objects = fieldExtractor.extractFields(payload);
        for (Object object : objects) {
            tokens.add(String.valueOf(object));
        }
        return tokens;
    }

    /**
     * Set the delimiter.
     *
     * @param delimiter to use
     */
    public void setDelimiter(char delimiter) {
        this.delimiter = delimiter;
    }

    /**
     * Set the qualifier.
     *
     * @param qualifier to use
     */
    public void setQualifier(char qualifier) {
        this.qualifier = qualifier;
    }
}
