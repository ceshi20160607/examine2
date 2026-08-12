package com.unique.examine.plat.vnext.manage.registration;

/** Test-profile seam for proving transaction rollback; it is never controlled by HTTP input. */
public interface RegistrationFailureProbe {
    void afterStep(RegistrationStep step);
}
