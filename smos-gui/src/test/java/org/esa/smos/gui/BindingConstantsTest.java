package org.esa.smos.gui;


import org.junit.Test;

import static org.junit.Assert.*;


public class BindingConstantsTest {

    @Test
    public void testBindingConstants() {
       assertEquals("useSelectedProduct", BindingConstants.SELECTED_PRODUCT);
       assertEquals("sourceDirectory", BindingConstants.SOURCE_DIRECTORY);
       assertEquals("openFileDialog", BindingConstants.OPEN_FILE_DIALOG);
       assertEquals("geometry", BindingConstants.GEOMETRY);
       assertEquals("roiType", BindingConstants.ROI_TYPE);

        assertEquals("compressionLevel", BindingConstants.COMPRESSION_LEVEL);
    }

    @Test
    public void testRoiTypeConstants() {
        assertEquals(0, BindingConstants.ROI_TYPE_ALL);
        assertEquals(1, BindingConstants.ROI_TYPE_GEOMETRY);
        assertEquals(2, BindingConstants.ROI_TYPE_BOUNDING_BOX);
    }
}
