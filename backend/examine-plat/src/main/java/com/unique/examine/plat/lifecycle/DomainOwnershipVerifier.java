package com.unique.examine.plat.lifecycle;

public interface DomainOwnershipVerifier {
    String RECORD_PREFIX = "_examine-challenge.";

    boolean hasTxtProof(String domainName, String verificationToken);

    static String recordName(String domainName) {
        return RECORD_PREFIX + domainName;
    }
}
