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
package org.easybatch.extensions.zip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.easybatch.core.listener.JobListener;
import org.easybatch.core.util.Utils;
import org.easybatch.extensions.AbstractCompressListener;

/**
 * A {@link JobListener} to compress files/folders with the ZIP archive format.
 * 
 * Implementation is inspired from article <a href=
 * "http://www.thinkcode.se/blog/2015/08/21/packaging-a-zip-file-from-java-using-apache-commons-compress">Packaging
 * a zip file from Java using Apache Commons compress</a>
 *
 * @author Somma Daniele
 */
public class CompressZipListener extends AbstractCompressListener {

  private static final Logger LOGGER = Logger.getLogger(CompressZipListener.class.getName());

  /**
   * Create a new {@link CompressZipListener}
   * 
   * @param out
   *          {@link File} created as output of the compression
   * @param in
   *          {@link File}'s must to be compress
   */
  public CompressZipListener(File out, File... in) {
    super(out, in);
  }

  @Override
  public void compress() {
    try {
      compress(out, in);
    } catch (IOException | ArchiveException e) {
      LOGGER.log(Level.SEVERE, "Error compress file: " + e.getMessage(), e);
    }
  }

  /**
   * Compress files/folders into zip file.
   * 
   * @param out
   *          zip output file
   * @param in
   *          files/folders to add
   * @throws ArchiveException
   * @throws IOException
   */
  private void compress(final File out, final File... in) throws ArchiveException, IOException {
    try (OutputStream os = new FileOutputStream(out);
        ArchiveOutputStream archive = new ArchiveStreamFactory().createArchiveOutputStream(ArchiveStreamFactory.ZIP, os)) {
      for (File file : in) {
        compress(file, archive);
      }
      archive.finish();
    }
  }

  private void compress(final File in, final ArchiveOutputStream archive) throws ArchiveException, IOException {
    if (in.isDirectory()) {
      String rootDir = in.getName();
      addDirEntry(archive, ArchiveStreamFactory.ZIP, rootDir);
      Collection<File> fileList = FileUtils.listFilesAndDirs(in, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
      fileList.remove(in);
      for (File file : fileList) {
        if (file.isDirectory()) {
          addDirEntry(archive, ArchiveStreamFactory.ZIP, rootDir + Utils.FILE_SEPARATOR + file.getName());
        } else {
          addFileEntry(archive, ArchiveStreamFactory.ZIP, file, getEntryName(in, file), rootDir);
        }
      }
    } else {
      addFileEntry(archive, ArchiveStreamFactory.ZIP, in);
    }
  }

}