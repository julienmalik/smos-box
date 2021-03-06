/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.smos.gui;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.ProgressMonitorSwingWorker;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.rcp.util.Dialogs;
import org.esa.snap.ui.SelectExportMethodDialog;
import org.jdesktop.swingx.table.TableColumnModelExt;

import javax.swing.JOptionPane;
import javax.swing.table.TableModel;
import java.awt.Component;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.concurrent.ExecutionException;

/**
 * A class to export a table model.
 *
 * @author Marco Peters
 * @version $Revision: $ $Date: $
 * @since BEAM 4.6
 */
public class TableModelExportRunner {

    private final Component parentComponent;
    private String title;
    private final TableModel model;
    private final TableColumnModelExt columnModel;

    /**
     * Creates an instance of this class to export a table model.
     *
     * @param parentComponent The parent component to align displayed dialogs.
     * @param title           The title of displayed dialogs.
     * @param model           the model to export.
     */
    public TableModelExportRunner(Component parentComponent, String title, TableModel model, TableColumnModelExt columnModel) {
        this.parentComponent = parentComponent;
        this.title = title;
        this.model = model;
        this.columnModel = columnModel;
    }

    /**
     * Starts the export process.
     */
    public void run() {
        if (model.getRowCount() == 0) {
            Dialogs.showMessage(title, "The table is empty!", JOptionPane.INFORMATION_MESSAGE, null);
        }

        // Get export method from user
        final int method = SelectExportMethodDialog.run(parentComponent, title,
                "How do you want to export the table?", "");
        if (method == SelectExportMethodDialog.EXPORT_CANCELED) {
            return;
        }

        final TableModelExporter exporter = new TableModelExporter(model, columnModel);
        if (method == SelectExportMethodDialog.EXPORT_TO_FILE) {
            final File outFile = promptForFile(title);
            if (outFile != null) {
                exportToFile(outFile, exporter);
            }
        } else if (method == SelectExportMethodDialog.EXPORT_TO_CLIPBOARD) {
            exportToClipboard(exporter);
        }
    }

    private void exportToFile(final File outFile, final TableModelExporter exporter) {
        final ProgressMonitorSwingWorker worker = new ProgressMonitorSwingWorker(parentComponent,
                "Table Model Export") {
            @Override
            protected Object doInBackground(ProgressMonitor pm) throws Exception {
                pm.beginTask("Exporting table model...", 1);
                try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(outFile))) {
                    exporter.export(stream);
                } finally {
                    pm.done();
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException ignore) {
                } catch (ExecutionException e) {
                    e.getCause().printStackTrace();
                    final String message = MessageFormat.format("The table could not be exported!\nReason: {0}", e.getCause().getMessage());
                    Dialogs.showMessage(title, message, JOptionPane.ERROR_MESSAGE, null);
                }
            }
        };
        worker.execute();
    }

    private void exportToClipboard(final TableModelExporter exporter) {
        final ProgressMonitorSwingWorker worker = new ProgressMonitorSwingWorker(parentComponent,
                "Table Model Export") {
            @Override
            protected Object doInBackground(ProgressMonitor pm) throws Exception {
                pm.beginTask("Exporting table model...", 1);
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (OutputStream stream = new BufferedOutputStream(out)) {
                    exporter.export(stream);
                } finally {
                    pm.done();
                }
                return out.toString();
            }

            @Override
            protected void done() {
                try {
                    final Object result = get();
                    if (result instanceof String) {
                        SystemUtils.copyToClipboard((String) result);
                    }
                } catch (InterruptedException ignore) {
                } catch (ExecutionException exex) {
                    final Throwable cause = exex.getCause();
                    cause.printStackTrace();
                    final String message = MessageFormat.format("The table could not be exported!\nReason: {0}",
                            cause.getMessage());
                    Dialogs.showMessage(title, message, JOptionPane.ERROR_MESSAGE, null);
                }
            }
        };
        worker.execute();
    }

    /**
     * Opens a modal file chooser dialog that prompts the user to select the output file name.
     *
     * @param defaultFileName The default file name.
     * @return The selected file, <code>null</code> means "Cancel".
     */
    private static File promptForFile(String defaultFileName) {
        // Loop while the user does not want to overwrite a selected, existing file
        // or if the user presses "Cancel"
        //
        final String dlgTitle = "Export Table";
        File file = null;
        while (file == null) {
            file = Dialogs.requestFileForSave(dlgTitle,
                                              false,
                                              null,
                                              ".txt",
                                              defaultFileName,
                                              null,
                                              "exportSmosTable.lastDir");

            if (file == null) {
                return null; // Cancel
            }
        }
        return file;
    }
}
