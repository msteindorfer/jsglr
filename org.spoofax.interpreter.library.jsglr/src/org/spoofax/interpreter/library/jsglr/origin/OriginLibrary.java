package org.spoofax.interpreter.library.jsglr.origin;

import org.spoofax.interpreter.library.AbstractStrategoOperatorRegistry;

/**
 * @author Lennart Kats <lennart add lclnet.nl>
 */
public class OriginLibrary extends AbstractStrategoOperatorRegistry {

    public static final String REGISTRY_NAME = "ORIGIN";

    public OriginLibrary() {
        add(new SSL_EXT_enable_origins());
        add(new SSL_EXT_clone_and_set_parents());
        add(new SSL_EXT_get_parent());
    }

    public String getOperatorRegistryName() {
        return REGISTRY_NAME;
    }
}