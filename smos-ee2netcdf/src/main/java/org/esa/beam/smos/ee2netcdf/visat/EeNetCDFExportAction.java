package org.esa.beam.smos.ee2netcdf.visat;


import org.esa.snap.rcp.actions.AbstractSnapAction;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle;

import java.awt.event.ActionEvent;

@ActionID(
        category = "File",
        id = "Ee2NetCDFExportAction"
)

@ActionRegistration(
        displayName = "#CTL_Ee2NetCDFExport_MenuText",
        popupText = "#CTL_Ee2NetCDFExport_ShortDescription",
        lazy = true
)

@ActionReferences({
        @ActionReference(
                path = "File/Export"
        )
})

@NbBundle.Messages({
        "CTL_Ee2NetCDFExport_MenuText=Export SMOS EE Files to NetCDF...",
        "CTL_Ee2NetCDFExport_ShortDescription=Export SMOS EE Files to linear NetCDF format"
})
public class EeNetCDFExportAction extends AbstractSnapAction {

    private static final String HELP_ID = "smosNetcdfExport";
    private NetcdfExportDialog dialog;

    public EeNetCDFExportAction() {
        putValue(NAME, Bundle.CTL_Ee2NetCDFExport_MenuText());
        putValue(SHORT_DESCRIPTION, Bundle.CTL_Ee2NetCDFExport_ShortDescription());
        setHelpId(HELP_ID);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        if (dialog != null) {
            dialog = new NetcdfExportDialog(getAppContext(), HELP_ID);
        }
        dialog.show();
    }
}
