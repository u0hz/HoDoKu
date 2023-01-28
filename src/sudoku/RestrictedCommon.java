/*
 * Copyright (C) 2008/09  Bernhard Hobiger
 *
 * This file is part of HoDoKu.
 *
 * HoDoKu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HoDoKu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HoDoKu. If not, see <http://www.gnu.org/licenses/>.
 */
package sudoku;

/**
 * Describes Restriced Commons (RCs) between two ALS; since we only
 * handle ALS and not AALS or greater a maximum of 2 RCs between
 * any ALS pair can exist.<br>
 * 
 * If only one RC exists for the pair, the second is 0.<br>
 * 
 * The references to the ALS atr stored as indices into <code>alses</code>.
 */
public class RestrictedCommon implements Comparable<RestrictedCommon>, Cloneable {
    public int als1; // index of first ALS (index into alses)
    public int als2; // index of second ALS (index into alses)
    public int cand1; // first rc, must be != 0
    public int cand2; // second rc; if cand2 == 0 only one rc exists between als1 and als2
    public int actualRC; // 0: none, 1: cand1 only, 2: cand2 only, 3: both

    public RestrictedCommon() {
    }
    
    public RestrictedCommon(int als1, int als2, int cand1) {
        this.als1 = als1;
        this.als2 = als2;
        this.cand1 = cand1;
        this.cand2 = 0;
    }

    public RestrictedCommon(int als1, int als2, int cand1, int cand2) {
        this(als1, als2, cand1);
        this.cand2 = cand2;
    }

    public RestrictedCommon(int als1, int als2, int cand1, int cand2, int actualRC) {
        this(als1, als2, cand1, cand2);
        this.actualRC = actualRC;
    }
    
    /**
     * New propagation rules for ALS-Chains: the actual RCs of parameter
     * rc are excluded from this, this.actualRC is adjusted as necessary;
     * if this.actualRC is greater than 0 the chain can be continued and
     * true is returned, else false is returned.
     * 
     * If a chain starts with a doubly linked RC Rc == null, cand2 != 0) , 
     * one of the RCs can be chosen freely; this results in two different
     * tries for the chain search.
     * 
     * @param rc RC of the last link in a chain
     * @param firstTry Only used, if rc == null: if set, cand1 is used else cand2
     * @return true if an actual RC remains, false otherwise
     */
    public boolean checkRC(RestrictedCommon rc, boolean firstTry) {
        actualRC = cand2 == 0 ? 1 : 3;
        // rc is not provided
        if (rc == null) {
            // start of chain: pick your RC
            if (cand2 != 0) {
                actualRC = firstTry ? 1 : 2;
            }
            return actualRC != 0;
        }
        switch (rc.actualRC) {
            case 0:
                // already done
                break;
            case 1:
                actualRC = checkRCInt(rc.cand1, 0, cand1, cand2);
                break;
            case 2:
                actualRC = checkRCInt(rc.cand2, 0, cand1, cand2);
                break;
            case 3:
                actualRC = checkRCInt(rc.cand1, rc.cand1, cand1, cand2);
                break;
            default:
                break;
        }
        return actualRC != 0;
    }

    /**
     * Checks duplicates (all possible combinations); c12 and c22
     * can be 0 (meaning: to be ignored).
     * 
     * @param c11 First ARC of first link
     * @param c12 Second ARC of first link (may be 0)
     * @param c21 First PRC of second link
     * @param c22 Second PRC of second link (may be 0)
     * @return
     */
    private int checkRCInt(int c11, int c12, int c21, int c22) {
        if (c12 == 0) {
            // one ARC
            if (c22 == 0) {
                // one ARC one PRC
                if (c11 == c21) {
                    return 0;
                } else {
                    return 1;
                }
            } else {
                // one ARC two PRCs
                if (c11 == c22) {
                    return 1;
                } else if (c11 == c21) {
                    return 2;
                } else {
                    return 3;
                }
            }
        } else {
            // two ARCs
            if (c22 == 0) {
                // two ARCs one PRC
                if (c11 == c21 || c12 == c21) {
                    return 0;
                } else {
                    return 1;
                }
            } else {
                // two ARCs two PRCs
                if ((c11 == c21 && c12 == c22) || (c11 == c22 && c12 == c21)) {
                    return 0;
                } else if (c11 == c22 || c12 == c22) {
                    return 1;
                } else if (c11 == c21 || c12 == c21) {
                    return 2;
                } else {
                    return 3;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "RC(" + als1 + "/" + als2 + "/" + cand1 + "/" + cand2 + "/" + actualRC + ")";
    }
    
    @Override
    public int compareTo(RestrictedCommon r) {
        int result = als1 - r.als1;
        if (result == 0) {
            result = als2 - r.als2;
            if (result == 0) {
                result = cand1 - r.cand1;
                if (result == 0) {
                    result = cand2 - r.cand2;
                }
            }
        }
        return result;
    }

    @Override
    public Object clone()
            throws CloneNotSupportedException {
        RestrictedCommon newRC = (RestrictedCommon) super.clone();
        return newRC;
    }

    public int getAls1() {
        return als1;
    }

    public void setAls1(int als1) {
        this.als1 = als1;
    }

    public int getAls2() {
        return als2;
    }

    public void setAls2(int als2) {
        this.als2 = als2;
    }

    public int getCand1() {
        return cand1;
    }

    public void setCand1(int cand1) {
        this.cand1 = cand1;
    }

    public int getCand2() {
        return cand2;
    }

    public void setCand2(int cand2) {
        this.cand2 = cand2;
    }

    public int getActualRC() {
        return actualRC;
    }

    public void setActualRC(int actualRC) {
        this.actualRC = actualRC;
    }
}
