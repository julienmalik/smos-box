package org.esa.smos.ee2netcdf.reader;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.support.DefaultMultiLevelImage;
import org.esa.smos.ObservationPointList;
import org.esa.smos.dataio.smos.*;
import org.esa.smos.dataio.smos.dddb.BandDescriptor;
import org.esa.smos.dataio.smos.dddb.Dddb;
import org.esa.smos.dataio.smos.dddb.Family;
import org.esa.smos.dataio.smos.dddb.FlagDescriptor;
import org.esa.smos.dgg.SmosDgg;
import org.esa.smos.ee2netcdf.AttributeEntry;
import org.esa.smos.ee2netcdf.MetadataUtils;
import org.esa.smos.lsmask.SmosLsMask;
import org.esa.smos.Point;
import org.esa.snap.dataio.netcdf.util.DataTypeUtils;
import org.esa.snap.dataio.netcdf.util.NetcdfFileOpener;
import org.esa.snap.framework.dataio.ProductReaderPlugIn;
import org.esa.snap.framework.datamodel.Band;
import org.esa.snap.framework.datamodel.Product;
import org.esa.snap.framework.datamodel.ProductData;
import ucar.ma2.Array;
import ucar.nc2.Attribute;
import ucar.nc2.NetcdfFile;
import ucar.nc2.Variable;

import java.awt.*;
import java.awt.geom.Area;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

public class NetcdfProductReader extends SmosReader {

    private static final String LSMASK_SCHEMA_NAME = "DBL_SM_XXXX_AUX_LSMASK_0200";

    private NetcdfFile netcdfFile;
    private Area area;
    private HashMap<Integer, Integer> seqNumToIndexMap;

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    protected NetcdfProductReader(ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    @Override
    public boolean canSupplyGridPointBtData() {
        return false;
    }

    @Override
    public boolean canSupplyFullPolData() {
        return false;
    }

    @Override
    public GridPointBtDataset getBtData(int gridPointIndex) throws IOException {
        return null;
    }

    @Override
    public int getGridPointIndex(int gridPointId) {
        return 0;
    }

    @Override
    public int getGridPointId(int levelPixelX, int levelPixelY, int currentLevel) {
        return 0;
    }

    @Override
    public String[] getRawDataTableNames() {
        return new String[0];
    }

    @Override
    public FlagDescriptor[] getBtFlagDescriptors() {
        return new FlagDescriptor[0];
    }

    @Override
    public PolarisationModel getPolarisationModel() {
        return null;
    }

    @Override
    public boolean canSupplySnapshotData() {
        return false;
    }

    @Override
    public boolean hasSnapshotInfo() {
        return false;
    }

    @Override
    public SnapshotInfo getSnapshotInfo() {
        return null;
    }

    @Override
    public Object[][] getSnapshotData(int snapshotIndex) throws IOException {
        return new Object[0][];
    }

    @Override
    protected Product readProductNodesImpl() throws IOException {
        Product product;

        synchronized (this) {
            final File inputFile = getInputFile();
            netcdfFile = NetcdfFileOpener.open(inputFile.getAbsolutePath());
            if (netcdfFile == null) {
                throw new IOException("Unable to read file");
            }

            final Attribute fileTypeAttrbute = netcdfFile.findGlobalAttribute("Fixed_Header:File_Type");
            if (fileTypeAttrbute == null) {
                throw new IOException("Required attribute `Fixed_Header:File_Type` not found");
            }
            product = ProductHelper.createProduct(inputFile, fileTypeAttrbute.getStringValue());

            addMetadata(product);

            area = calculateArea();

            seqNumToIndexMap = calculateSeqNumToIndexmap();

            final String schemaDescription = getSchemaDescription();
            final Family<BandDescriptor> bandDescriptors = Dddb.getInstance().getBandDescriptors(schemaDescription);
            if (bandDescriptors == null) {
                throw new IOException("Unsupported file schema: '" + schemaDescription + "`");
            }

            for (final BandDescriptor descriptor : bandDescriptors.asList()) {
                if (!descriptor.isVisible()) {
                    continue;
                }
                final Variable variable = netcdfFile.findVariable(null, descriptor.getMemberName());
                if (variable == null) {
                    continue;
                }
                final int rasterDataType = DataTypeUtils.getRasterDataType(variable);
                final Band band = product.addBand(variable.getFullName(), rasterDataType);
                band.setScalingOffset(descriptor.getScalingOffset());
                band.setScalingFactor(descriptor.getScalingFactor());
                if (descriptor.hasFillValue()) {
                    band.setNoDataValueUsed(true);
                    band.setNoDataValue(descriptor.getFillValue());
                }
                if (!descriptor.getValidPixelExpression().isEmpty()) {
                    band.setValidPixelExpression(descriptor.getValidPixelExpression());
                }
                if (!descriptor.getUnit().isEmpty()) {
                    band.setUnit(descriptor.getUnit());
                }
                if (!descriptor.getDescription().isEmpty()) {
                    band.setDescription(descriptor.getDescription());
                }
                if (descriptor.getFlagDescriptors() != null) {
                    ProductHelper.addFlagsAndMasks(product, band,
                            descriptor.getFlagCodingName(),
                            descriptor.getFlagDescriptors());
                }

                final VariableValueProvider variableValueProvider = new VariableValueProvider(variable, area);
                SmosMultiLevelSource smosMultiLevelSource = new SmosMultiLevelSource(band, variableValueProvider);
                DefaultMultiLevelImage defaultMultiLevelImage = new DefaultMultiLevelImage(smosMultiLevelSource);
                band.setSourceImage(defaultMultiLevelImage);
                band.setImageInfo(ProductHelper.createImageInfo(band, descriptor));
            }

            addLandSeaMask(product);
        }

        return product;
    }

    private HashMap<Integer, Integer> calculateSeqNumToIndexmap() throws IOException {
        final Variable gridPointIdVariable = netcdfFile.findVariable(null, "Grid_Point_ID");
        final Array gridPointIdArray = gridPointIdVariable.read();
        final int[] shape = gridPointIdArray.getShape();
        final HashMap<Integer, Integer> seqNumToIndexMap = new HashMap<>(shape[0]);

        for (int i = 0; i < shape[0]; i++) {
            final int gridPointId = gridPointIdArray.getInt(i);
            final int seqnum = SmosDgg.gridPointIdToSeqnum(gridPointId);
            seqNumToIndexMap.put(seqnum, i);
        }
        return seqNumToIndexMap;
    }

    private Area calculateArea() throws IOException {
        // @todo 1 tb/tb extract class for L1C , L2 and Browse access

//        final Variable latitude = netcdfFile.findVariable(null, "Grid_Point_Latitude");
//        final Variable longitude = netcdfFile.findVariable(null, "Grid_Point_Longitude");

        final Variable latitude = netcdfFile.findVariable(null, "Latitude");
        final Variable longitude = netcdfFile.findVariable(null, "Longitude");
        if (latitude == null || longitude == null) {
            throw new IOException("Missing geo location variables");
        }

        final Array latitudeArray = latitude.read();
        final Array longitudeArray = longitude.read();

        final int[] shape = longitudeArray.getShape();
        final Point[] pointArray = new Point[shape[0]];
        for (int i = 0; i < shape[0]; i++) {
            pointArray[i] = new Point(longitudeArray.getDouble(i), latitudeArray.getDouble(i));
        }

        return DggUtils.computeArea(new ObservationPointList(pointArray));
    }

    private void addMetadata(Product product) {
        final List<Attribute> globalAttributes = netcdfFile.getGlobalAttributes();
        final List<AttributeEntry> attributeEntries = MetadataUtils.convertNetcdfAttributes(globalAttributes);
        MetadataUtils.parseMetadata(attributeEntries, product.getMetadataRoot());
    }

    @Override
    protected final void readBandRasterDataImpl(int sourceOffsetX,
                                                int sourceOffsetY,
                                                int sourceWidth,
                                                int sourceHeight,
                                                int sourceStepX,
                                                int sourceStepY,
                                                Band targetBand,
                                                int targetOffsetX,
                                                int targetOffsetY,
                                                int targetWidth,
                                                int targetHeight,
                                                ProductData targetBuffer,
                                                ProgressMonitor pm) {
        synchronized (this) {
            final RenderedImage image = targetBand.getSourceImage();
            final Raster data = image.getData(new Rectangle(targetOffsetX, targetOffsetY, targetWidth, targetHeight));

            data.getDataElements(targetOffsetX, targetOffsetY, targetWidth, targetHeight, targetBuffer.getElems());
        }
    }

    @Override
    public void close() throws IOException {
        if (netcdfFile != null) {
            netcdfFile.close();
            netcdfFile = null;
        }
    }

    private void addLandSeaMask(Product product) {
        final BandDescriptor descriptor = Dddb.getInstance().getBandDescriptors(LSMASK_SCHEMA_NAME).getMember(SmosConstants.LAND_SEA_MASK_NAME);

        final Band band = product.addBand(descriptor.getBandName(), ProductData.TYPE_UINT8);

        band.setScalingOffset(descriptor.getScalingOffset());
        band.setScalingFactor(descriptor.getScalingFactor());
        if (descriptor.hasFillValue()) {
            band.setNoDataValueUsed(true);
            band.setNoDataValue(descriptor.getFillValue());
        }
        if (!descriptor.getValidPixelExpression().isEmpty()) {
            band.setValidPixelExpression(descriptor.getValidPixelExpression());
        }
        if (!descriptor.getUnit().isEmpty()) {
            band.setUnit(descriptor.getUnit());
        }
        if (!descriptor.getDescription().isEmpty()) {
            band.setDescription(descriptor.getDescription());
        }
        if (descriptor.getFlagDescriptors() != null) {
            ProductHelper.addFlagsAndMasks(product, band, descriptor.getFlagCodingName(),
                    descriptor.getFlagDescriptors());
        }

        band.setSourceImage(SmosLsMask.getInstance().getMultiLevelImage());
        band.setImageInfo(ProductHelper.createImageInfo(band, descriptor));
    }

    private String getSchemaDescription() throws IOException {
        final Attribute schemaAttribute = netcdfFile.findGlobalAttribute("Variable_Header:Specific_Product_Header:Main_Info:Datablock_Schema");
        if (schemaAttribute == null) {
            throw new IOException("Schema attribuite not found.");
        }

        return schemaAttribute.getStringValue().substring(0, 27);
    }
}
