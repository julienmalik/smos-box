package org.esa.beam.dataio.smos;

import org.esa.beam.framework.datamodel.Product;

import java.util.HashMap;

class FprGPVP extends FpGPVP {

    protected FprGPVP(Product product, HashMap<String, GridPointValueProvider> valueProviderMap, boolean accuracy) {
        super(product, valueProviderMap, accuracy, false);
    }

    @Override
    protected double compute(double btx, double bty, double btxy, double aa, double ab, double bb) {
        return ab * (btx - bty) + (aa - bb) * btxy;
    }
}