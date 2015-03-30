package org.esa.beam.smos.ee2netcdf.ui;

import org.esa.beam.smos.ee2netcdf.TestHelper;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class NetcdfExportDialogTest {

    @Test
    public void testGetTargetFiles_singleProduct() throws IOException {
        final File resourceFile = TestHelper.getResourceFile("SM_OPER_MIR_BWLF1C_20111026T143206_20111026T152520_503_001_1.zip");
        final String filePath = resourceFile.getAbsolutePath();
        final File targetDir = new File("/some/not/existing/dir");


        final List<File> targetFiles = NetcdfExportDialog.getTargetFiles(filePath, targetDir);
        assertNotNull(targetFiles);
        assertEquals(1, targetFiles.size());
        assertEquals(new File(targetDir, "SM_OPER_MIR_BWLF1C_20111026T143206_20111026T152520_503_001_1.nc").getAbsolutePath(),
                targetFiles.get(0).getAbsolutePath());
    }

    @Test
    public void testGetTargetFiles_directory() throws IOException {
        final Path resourceDirectory = TestHelper.getResourceDirectory();
        final File targetDir = new File("/some/not/existing/dir");

        final String wildCardpath = resourceDirectory.toAbsolutePath().toString() + File.separator + "*.zip";
        final List<File> targetFiles = NetcdfExportDialog.getTargetFiles(wildCardpath, targetDir);
        assertNotNull(targetFiles);
        assertEquals(4, targetFiles.size());
        assertEquals(new File(targetDir, "SM_OPER_MIR_BWLF1C_20111026T143206_20111026T152520_503_001_1.nc").getAbsolutePath(),
                targetFiles.get(0).getAbsolutePath());
        assertEquals(new File(targetDir, "SM_OPER_MIR_OSUDP2_20091204T001853_20091204T011255_310_001_1.nc").getAbsolutePath(),
                targetFiles.get(1).getAbsolutePath());
        assertEquals(new File(targetDir, "SM_OPER_MIR_SMUDP2_20120514T163815_20120514T173133_551_001_1.nc").getAbsolutePath(),
                targetFiles.get(2).getAbsolutePath());
        assertEquals(new File(targetDir, "SM_REPB_MIR_SCLF1C_20110201T151254_20110201T151308_505_152_1.nc").getAbsolutePath(),
                targetFiles.get(3).getAbsolutePath());
    }

    @Test
    public void testGetExistingFiles_noneExists() {
        final ArrayList<File> targetFiles = new ArrayList<>();
        targetFiles.add(new File("/fantasy/location/target/file"));
        targetFiles.add(new File("/not/existing/file"));

        final List<File> existingFiles = NetcdfExportDialog.getExistingFiles(targetFiles);
        assertNotNull(existingFiles);
        assertEquals(0, existingFiles.size());
    }

    @Test
    public void testGetExistingFiles_oneExists() {
        final ArrayList<File> targetFiles = new ArrayList<>();
        targetFiles.add(new File("/fantasy/location/target/file"));
        targetFiles.add(TestHelper.getResourceFile("SM_OPER_MIR_OSUDP2_20091204T001853_20091204T011255_310_001_1.zip"));

        final List<File> existingFiles = NetcdfExportDialog.getExistingFiles(targetFiles);
        assertNotNull(existingFiles);
        assertEquals(1, existingFiles.size());
    }

    @Test
    public void testGetExistingFiles_twoExists() {
        final ArrayList<File> targetFiles = new ArrayList<>();
        targetFiles.add(TestHelper.getResourceFile("SM_OPER_MIR_BWLF1C_20111026T143206_20111026T152520_503_001_1.zip"));
        targetFiles.add(new File("/fantasy/location/target/file"));
        targetFiles.add(TestHelper.getResourceFile("SM_OPER_MIR_OSUDP2_20091204T001853_20091204T011255_310_001_1.zip"));

        final List<File> existingFiles = NetcdfExportDialog.getExistingFiles(targetFiles);
        assertNotNull(existingFiles);
        assertEquals(2, existingFiles.size());
    }

    @Test
    public void testListToString() {
        final File file_1 = TestHelper.getResourceFile("SM_OPER_MIR_BWLF1C_20111026T143206_20111026T152520_503_001_1.zip");
        final File file_2 = TestHelper.getResourceFile("SM_OPER_MIR_OSUDP2_20091204T001853_20091204T011255_310_001_1.zip");

        final ArrayList<File> targetFiles = new ArrayList<>();
        targetFiles.add(file_1);
        targetFiles.add(file_2);

        assertEquals(file_1.getAbsolutePath() + "\n" + file_2.getAbsolutePath() + "\n", NetcdfExportDialog.listToString(
                targetFiles));
    }

    @Test
    public void testListToString_ellipseAfterTenFiles() {

        final ArrayList<File> targetFiles = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            targetFiles.add(new File("blabla_" + i));
        }

        assertTrue(NetcdfExportDialog.listToString(targetFiles).contains("..."));
    }

}

