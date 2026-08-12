package com.unique.examine.plat.lifecycle;

import org.springframework.stereotype.Component;

import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

@Component
public class DnsTxtDomainOwnershipVerifier implements DomainOwnershipVerifier {
    @Override
    public boolean hasTxtProof(String domainName, String verificationToken) {
        var environment = new Hashtable<String, String>();
        environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        environment.put("com.sun.jndi.dns.timeout.initial", "2000");
        environment.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            var attributes = new InitialDirContext(environment).getAttributes(
                    DomainOwnershipVerifier.recordName(domainName), new String[]{"TXT"});
            var values = attributes.get("TXT");
            if (values == null) return false;
            var entries = values.getAll();
            while (entries.hasMore()) {
                var value = String.valueOf(entries.next()).replace("\"", "").replace(" ", "");
                if (verificationToken.equals(value)) return true;
            }
            return false;
        } catch (Exception unavailableOrMissing) {
            return false;
        }
    }
}
