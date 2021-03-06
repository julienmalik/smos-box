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

package org.esa.smos.gui.export;

import com.bc.ceres.binding.Property;
import com.bc.ceres.binding.PropertyContainer;
import com.bc.ceres.binding.PropertyDescriptor;
import com.bc.ceres.binding.ValidationException;
import com.bc.ceres.binding.ValueSet;
import com.bc.ceres.swing.TableLayout;
import com.bc.ceres.swing.binding.Binding;
import com.bc.ceres.swing.binding.BindingContext;
import org.esa.smos.dataio.smos.ProductFile;
import org.esa.smos.dataio.smos.SmosFile;
import org.esa.smos.dataio.smos.SmosProductReader;
import org.esa.smos.gui.BindingConstants;
import org.esa.smos.gui.ChooserFactory;
import org.esa.smos.gui.DefaultChooserFactory;
import org.esa.smos.gui.ProductChangeAwareDialog;
import org.esa.smos.gui.swing.GuiHelper;
import org.esa.snap.core.dataio.ProductReader;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductManager;
import org.esa.snap.core.datamodel.VectorDataNode;
import org.esa.snap.core.gpf.annotations.ParameterDescriptorFactory;
import org.esa.snap.rcp.SnapApp;
import org.esa.snap.rcp.util.SelectionSupport;
import org.esa.snap.ui.AppContext;
import org.esa.snap.ui.FileChooserFactory;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class GridPointExportDialog extends ProductChangeAwareDialog {

    static final String ALIAS_RECURSIVE = "recursive";
    static final String ALIAS_TARGET_FILE = "targetFileOrDir";
    static final String ALIAS_EXPORT_FORMAT = "exportFormat";
    static final String NAME_CSV = "CSV";

    static final String NAME_EEF = "Earth Explorer subsets";
    private final AppContext appContext;
    private final PropertyContainer propertyContainer;
    private final BindingContext bindingContext;
    private GeometryListener geometryListener;
    private final GridPointExportParameter gridPointExportParameter;

    GridPointExportDialog(final AppContext appContext, String helpId) {
        super(appContext.getApplicationWindow(), "Export SMOS Grid Points", ID_OK | ID_CLOSE | ID_HELP, helpId); /* I18N */
        gridPointExportParameter = new GridPointExportParameter();
        this.appContext = appContext;

        propertyContainer = PropertyContainer.createObjectBacked(gridPointExportParameter, new ParameterDescriptorFactory());
        try {
            initPropertyContainer();
        } catch (ValidationException e) {
            throw new IllegalStateException(e); // cannot happen
        }

        bindingContext = new BindingContext(propertyContainer);
        bindingContext.bindEnabledState(BindingConstants.GEOMETRY, true, BindingConstants.ROI_TYPE, BindingConstants.ROI_TYPE_ALL);
        GuiHelper.bindLonLatPanelToRoiType(2, bindingContext);

        createUI();

        final ProductManager productManager = appContext.getProductManager();
        productManager.addListener(new ProductManagerListener(this));

        geometryListener = new GeometryListener(this);

        final SelectionSupport<Product> selectionSupport = SnapApp.getDefault().getSelectionSupport(Product.class);
        selectionSupport.addHandler(this);
    }

    @Override
    protected void onOK() {
        final File sourceDirectory = propertyContainer.getValue(BindingConstants.SOURCE_DIRECTORY);
        final File targetFile = propertyContainer.getValue(ALIAS_TARGET_FILE);
        GuiHelper.setDefaultSourceDirectory(sourceDirectory, appContext);
        setDefaultTargetFile(targetFile);
        if (targetFile.exists() && targetFile.isFile()) {
            final String message = MessageFormat.format(
                    "The selected target file\n''{0}''\nalready exists.\n\nDo you want to overwrite the target file?",
                    targetFile.getPath());
            final int answer = JOptionPane.showConfirmDialog(getJDialog(), message, getTitle(),
                    JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        final GridPointExportSwingWorker swingWorker =
                new GridPointExportSwingWorker(appContext, gridPointExportParameter.getClone());

        GuiHelper.setDefaultSourceDirectory(gridPointExportParameter.getSourceDirectory(), appContext);
        final File exportedFile = gridPointExportParameter.getTargetFile();
        if (exportedFile != null) {
            GuiHelper.setDefaultTargetDirectory(exportedFile.getParentFile(), appContext);
        }

        swingWorker.execute();
    }

    @Override
    protected boolean verifyUserInput() {
        final int roiType = propertyContainer.getValue(BindingConstants.ROI_TYPE);
        if (roiType == 2) {
            final double north = propertyContainer.getValue(BindingConstants.NORTH);
            final double south = propertyContainer.getValue(BindingConstants.SOUTH);
            if (north <= south) {
                showErrorDialog("The southern latitude must be less than the northern latitude.");
                return false;
            }
            final double east = propertyContainer.getValue(BindingConstants.EAST);
            final double west = propertyContainer.getValue(BindingConstants.WEST);
            if (east <= west) {
                showErrorDialog("The western longitude must be less than the eastern longitude.");
                return false;
            }
        }
        final File targetFile = propertyContainer.getValue(ALIAS_TARGET_FILE);
        final String exportFormat = propertyContainer.getValue(ALIAS_EXPORT_FORMAT);
        if (NAME_CSV.equals(exportFormat)) {
            if (targetFile.exists() && !targetFile.isFile()) {
                showErrorDialog("The target file must be a normal file, not a directory.");
                return false;
            }
        } else {
            if (targetFile.exists() && !targetFile.isDirectory()) {
                showErrorDialog("The target file must be a directory, not a normal file.");
                return false;
            }
        }
        return true;
    }

    private void initPropertyContainer() throws ValidationException {
        propertyContainer.setDefaultValues();

        final File defaultSourceDirectory = GuiHelper.getDefaultSourceDirectory(appContext);
        propertyContainer.setValue(BindingConstants.SOURCE_DIRECTORY, defaultSourceDirectory);

        final File targetFile = getDefaultTargetFile();
        propertyContainer.setValue(ALIAS_TARGET_FILE, targetFile);

        updateSelectedProductAndGeometries();
        if (targetFile.isDirectory()) {
            propertyContainer.setValue(ALIAS_EXPORT_FORMAT, NAME_EEF);
        }
        propertyContainer.addPropertyChangeListener(ALIAS_EXPORT_FORMAT, new ExportFormatChangeListener());
    }

    @SuppressWarnings("ConstantConditions")
    private void updateSelectedProductAndGeometries() throws ValidationException {
        final Product selectedProduct = getSelectedSmosProduct();
        if (selectedProduct != null) {
            final List<VectorDataNode> geometryNodeList = GuiHelper.getGeometryNodes(selectedProduct);
            if (!geometryNodeList.isEmpty()) {
                GuiHelper.bindGeometryNodes(geometryNodeList, propertyContainer);
            } else {
                removeGeometries();
                if (selectedProduct.getPinGroup().getNodeCount() != 0) {
                    propertyContainer.setValue(BindingConstants.ROI_TYPE, BindingConstants.ROI_TYPE_GEOMETRY);
                }
            }
        }
//        if (bindingContext != null) {
//            bindingContext.setComponentsEnabled(BindingConstants.SELECTED_PRODUCT, selectedProduct != null);
//        }
        propertyContainer.setValue(BindingConstants.SELECTED_PRODUCT, selectedProduct != null);
        setSelectedProductButtonEnabled(selectedProduct != null);
    }


    private void removeProductAndGeometries(Product product) throws ValidationException {
        final Product selectedSmosProduct = getSelectedSmosProduct();
        if (selectedSmosProduct == null) {
            propertyContainer.setValue(BindingConstants.SELECTED_PRODUCT, false);
            setSelectedProductButtonEnabled(false);

            final List<VectorDataNode> geometryNodeList = GuiHelper.getGeometryNodes(product);
            if (!geometryNodeList.isEmpty()) {
                removeGeometries();
            }
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void setSelectedProductButtonEnabled(boolean enabled) {
        if (bindingContext == null) {
            return;
        }
        final Binding binding = bindingContext.getBinding(BindingConstants.SELECTED_PRODUCT);
        final JComponent[] components = binding.getComponents();
        for (final JComponent component : components) {
            if (component instanceof JRadioButton) {
                if (((JRadioButton) component).getText().equals(BindingConstants.USE_SELECTED_PRODUCT_BUTTON_NAME)) {
                    component.setEnabled(enabled);
                    break;
                }
            }
        }
    }

    private void removeGeometries() throws ValidationException {
        final Property geometryProperty = propertyContainer.getProperty(BindingConstants.GEOMETRY);
        geometryProperty.getDescriptor().setValueSet(new ValueSet(new VectorDataNode[0]));
        propertyContainer.setValue(BindingConstants.GEOMETRY, null);
        propertyContainer.setValue(BindingConstants.ROI_TYPE, BindingConstants.ROI_TYPE_BOUNDING_BOX);
    }

    private void createUI() {
        final JPanel mainPanel = GuiHelper.createPanelWithBoxLayout();

        mainPanel.add(createSourceProductPanel());
        mainPanel.add(createRoiPanel());
        mainPanel.add(createTargetFilePanel());

        setContent(mainPanel);
    }

    private JComponent createSourceProductPanel() {
        final boolean useSelectProductEnabled = getSelectedSmosProduct() != null;

        final TableLayout layout = GuiHelper.createWeightedTableLayout(1);
        final JPanel sourceProductPanel = new JPanel(layout);
        sourceProductPanel.setBorder(BorderFactory.createTitledBorder("Source Products"));

        GuiHelper.addSourceProductsButtons(sourceProductPanel, useSelectProductEnabled, bindingContext);

        final JCheckBox checkBox = new JCheckBox("Descend into subdirectories");
        bindingContext.bind(ALIAS_RECURSIVE, checkBox);
        bindingContext.bindEnabledState(ALIAS_RECURSIVE, true, BindingConstants.SELECTED_PRODUCT, false);

        final PropertyDescriptor sourceDirectoryDescriptor = propertyContainer.getDescriptor(BindingConstants.SOURCE_DIRECTORY);
        final JComponent fileEditor = GuiHelper.createFileEditorComponent(sourceDirectoryDescriptor, new DefaultChooserFactory(), bindingContext);

        layout.setCellPadding(2, 0, new Insets(0, 24, 3, 3));
        sourceProductPanel.add(fileEditor);
        layout.setCellPadding(3, 0, new Insets(0, 24, 3, 3));
        sourceProductPanel.add(checkBox);

        return sourceProductPanel;
    }

    private Component createRoiPanel() {
        final JRadioButton useGeometryButton = new JRadioButton("Geometry");
        final PropertyDescriptor geometryDescriptor = propertyContainer.getDescriptor(BindingConstants.GEOMETRY);
        if (geometryDescriptor != null && geometryDescriptor.getValueSet() == null) {
            useGeometryButton.setEnabled(false);
        }

        final JRadioButton usePinsButton = new JRadioButton("Pins");
        final Product selectedProduct = getSelectedSmosProduct();
        if (selectedProduct == null || selectedProduct.getPinGroup().getNodeCount() == 0) {
            usePinsButton.setEnabled(false);
        }

        final JRadioButton useAreaButton = new JRadioButton("Area");
        final Map<AbstractButton, Object> buttonGroupValueSet = new HashMap<>();
        buttonGroupValueSet.put(useGeometryButton, 0);
        buttonGroupValueSet.put(usePinsButton, 1);
        buttonGroupValueSet.put(useAreaButton, 2);

        final ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(useGeometryButton);
        buttonGroup.add(usePinsButton);
        buttonGroup.add(useAreaButton);
        bindingContext.bind(BindingConstants.ROI_TYPE, buttonGroup, buttonGroupValueSet);

        final JComboBox geometryComboBox = GuiHelper.createGeometryNodeComboBox(geometryDescriptor, bindingContext);

        final TableLayout layout = GuiHelper.createWeightedTableLayout(1);
        final JPanel roiPanel = new JPanel(layout);
        roiPanel.setBorder(BorderFactory.createTitledBorder("Region of Interest"));

        roiPanel.add(useGeometryButton);
        roiPanel.add(geometryComboBox);
        roiPanel.add(usePinsButton);
        roiPanel.add(useAreaButton);
        final Component latLonPanel = GuiHelper.createLatLonPanel(propertyContainer, bindingContext);
        roiPanel.add(latLonPanel);

        layout.setCellPadding(1, 0, new Insets(0, 24, 3, 3));
        layout.setCellPadding(3, 0, new Insets(0, 24, 3, 3));

        return roiPanel;
    }

    private JComponent createTargetFilePanel() {
        final TableLayout layout = GuiHelper.createWeightedTableLayout(1);

        final JPanel targetFilePanel = new JPanel(layout);
        targetFilePanel.setBorder(BorderFactory.createTitledBorder("Target File"));

        final PropertyDescriptor formatDescriptor = propertyContainer.getDescriptor(ALIAS_EXPORT_FORMAT);
        final JComboBox<Object> formatComboBox = new JComboBox<>(formatDescriptor.getValueSet().getItems());
        bindingContext.bind(ALIAS_EXPORT_FORMAT, formatComboBox);

        final JPanel formatPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        formatPanel.add(new JLabel("Export as:"));
        formatPanel.add(formatComboBox);

        final JLabel label = new JLabel();
        if (NAME_CSV.equals(propertyContainer.getValue(ALIAS_EXPORT_FORMAT))) {
            label.setText("Save to file:");
        } else {
            label.setText("Save subset files to directory:");
        }
        final PropertyDescriptor targetFileDescriptor = propertyContainer.getDescriptor(ALIAS_TARGET_FILE);
        final ChooserFactory chooserFactory = file -> {
            final FileChooserFactory fileChooserFactory = FileChooserFactory.getInstance();
            final JFileChooser fileChooser;
            if (NAME_CSV.equals(bindingContext.getBinding(ALIAS_EXPORT_FORMAT).getPropertyValue())) {
                fileChooser = fileChooserFactory.createFileChooser(file);
                fileChooser.setAcceptAllFileFilterUsed(true);
                fileChooser.setFileFilter(new FileNameExtensionFilter("CSV", "CSV"));
            } else {
                fileChooser = fileChooserFactory.createDirChooser(file);
            }
            return fileChooser;
        };
        final JComponent fileEditor = GuiHelper.createFileEditorComponent(targetFileDescriptor, chooserFactory, bindingContext);
        targetFilePanel.add(formatPanel);
        targetFilePanel.add(label);
        targetFilePanel.add(fileEditor);

        bindingContext.addPropertyChangeListener(ALIAS_EXPORT_FORMAT, propertyChangeEvent -> {
            if (NAME_CSV.equals(propertyChangeEvent.getNewValue())) {
                label.setText("Save to file:");
            } else {
                label.setText("Save subset files to directory:");
            }
        });
        return targetFilePanel;
    }

    private File getDefaultTargetFile() {
        final String def = new File(System.getProperty("user.home", "."), "export.csv").getPath();
        return new File(appContext.getPreferences().getPropertyString(GuiHelper.LAST_TARGET_FILE_KEY, def));
    }

    private void setDefaultTargetFile(File targetFile) {
        appContext.getPreferences().setPropertyString(GuiHelper.LAST_TARGET_FILE_KEY, targetFile.getPath());
    }

    private Product getSelectedSmosProduct() {
        final Product selectedProduct = appContext.getSelectedProduct();

        if (selectedProduct != null) {
            final ProductReader productReader = selectedProduct.getProductReader();
            if (productReader instanceof SmosProductReader) {
                final SmosProductReader smosProductReader = (SmosProductReader) productReader;
                final ProductFile productFile = smosProductReader.getProductFile();
                if (productFile instanceof SmosFile) {
                    selectedProduct.addProductNodeListener(geometryListener);
                    return selectedProduct;
                }
            }
        }

        return null;
    }

    @Override
    protected void productAdded() {
        try {
            updateSelectedProductAndGeometries();
        } catch (ValidationException e) {
            showErrorDialog("Internal error: " + e.getMessage());
        }
    }

    @Override
    protected void productRemoved(Product product) {
        try {
            removeProductAndGeometries(product);
        } catch (ValidationException e) {
            showErrorDialog("Internal error: " + e.getMessage());
        }
        product.removeProductNodeListener(geometryListener);
    }

    @Override
    protected void geometryAdded() {
        try {
            updateSelectedProductAndGeometries();
        } catch (ValidationException e) {
            showErrorDialog("Internal error: " + e.getMessage());
        }
    }

    @Override
    protected void geometryRemoved() {
        try {
            updateSelectedProductAndGeometries();
        } catch (ValidationException e) {
            showErrorDialog("Internal error: " + e.getMessage());
        }
    }

    @Override
    protected void productSelectionChanged() {
        try {
            updateSelectedProductAndGeometries();
        } catch (ValidationException e) {
            showErrorDialog("Internal error: " + e.getMessage());
        }
    }

    @Override
    protected void onClose() {
        final SelectionSupport<Product> selectionSupport = SnapApp.getDefault().getSelectionSupport(Product.class);
        selectionSupport.removeHandler(this);
        super.onClose();
    }

    private class ExportFormatChangeListener implements PropertyChangeListener {

        File last;

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (NAME_CSV.equals(evt.getNewValue())) {
                final File dir = propertyContainer.getValue(ALIAS_TARGET_FILE);
                if (last != null) {
                    propertyContainer.setValue(ALIAS_TARGET_FILE, new File(dir, last.getName()));
                }
            } else {
                last = propertyContainer.getValue(ALIAS_TARGET_FILE);
                propertyContainer.setValue(ALIAS_TARGET_FILE, last.getParentFile());
            }
        }
    }
}
