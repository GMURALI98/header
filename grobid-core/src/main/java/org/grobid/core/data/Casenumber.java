package org.grobid.core.data;

import org.grobid.core.utilities.TextUtilities;

public class Casenumber {
    
    private String casenumber = null;

    public Casenumber(String cn) {
        casenumber = cn;
    }

    public String getCasenumber() {
        return casenumber;
    }

    public void setCasenumber(String cn) {
        casenumber = cn;
    }
	
    public boolean notNull() {
        if (casenumber == null)
            return false;
        else
            return true;
    }

    public String toString() {
        String res = "";
        if (casenumber != null)
            res += casenumber + " ";
        
        return res.trim();
    }
    public String toTEI() {
        if (casenumber == null) {
            return null;
        }
        String res = "<casenumber>" + TextUtilities.HTMLEncode(casenumber) + "</casenumber>";
        return res;
    }

}
