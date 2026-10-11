package com.roaddamage.governance;

/**
 * Statutory road administration jurisdictions for public infrastructure maintenance.
 */
public enum Jurisdiction {

    /** State Public Works Department (PWD) Highway network. */
    STATE_HIGHWAY("State Highway (PWD)"),

    /** National Highways Authority of India (NHAI) arterial corridors. */
    NHAI("National Highways Authority of India (NHAI)"),

    /** Municipal Corporation / Town Municipal Council / Urban Local Body. */
    URBAN_LOCAL_BODY("Urban Local Body (Municipal Corp)");

    private final String agencyName;

    Jurisdiction(String agencyName) {
        this.agencyName = agencyName;
    }

    /**
     * Resolves formal agency name.
     *
     * @return agency label
     */
    public String agencyName() {
        return agencyName;
    }
}
