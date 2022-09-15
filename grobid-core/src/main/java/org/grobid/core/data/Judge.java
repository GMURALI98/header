package org.grobid.core.data;

import org.grobid.core.utilities.TextUtilities;

public class Judge {
    
    private String judge = null;

    public Judge(String j) {
        judge = j;
    }

    public String getJudge() {
        return judge;
    }

    public void setJudge(String j) {
        judge = j;
    }
	
    public boolean notNull() {
        if (judge == null)
            return false;
        else
            return true;
    }

    public String toString() {
        String res = "";
        if (judge != null)
            res += judge + " ";
        
        return res.trim();
    }
    public String toTEI() {
        if (judge == null) {
            return null;
        }
        String res = "<judges>" + TextUtilities.HTMLEncode(judge) + "</judges>";
        return res;
    }

}
