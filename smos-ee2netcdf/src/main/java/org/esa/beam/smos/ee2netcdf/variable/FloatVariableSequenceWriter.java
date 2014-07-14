package org.esa.beam.smos.ee2netcdf.variable;

import com.bc.ceres.binio.CompoundData;
import com.bc.ceres.binio.SequenceData;
import org.esa.beam.dataio.netcdf.nc.NVariable;
import ucar.ma2.Array;

import java.io.IOException;

class FloatVariableSequenceWriter extends AbstractVariableWriter {

    private final int memberIndex;
    private final int compoundIndex;

    FloatVariableSequenceWriter(NVariable variable, int arraySize, int memberIndex, int compoundIndex) {
        this.variable = variable;
        this.memberIndex = memberIndex;
        this.compoundIndex = compoundIndex;
        array = Array.factory(new float[arraySize]);
    }

    @Override
    public void write(CompoundData gridPointData, SequenceData btDataList, int index) throws IOException {
        final float data = btDataList.getCompound(0).getFloat(memberIndex);
        array.setFloat(index, data);
    }
}
