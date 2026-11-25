package com.cineconecta.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class PlatformApplicationTests {

    @Test
    void verifiesApplicationModulesAndCycleFreedom() {
        ApplicationModules modules = ApplicationModules.of(PlatformApplication.class);
        modules.verify();
    }
}
