package com.example.springboot.homeassistant.models;

import java.util.Set;

public final class LightEntityClassifier {

    private LightEntityClassifier() {
    }

    public enum LightKind {
        GROUP,
        ON_OFF_ONLY,
        DIMMABLE_OR_COLOR,
        UNKNOWN
    }

    public static LightKind classify(LightEntity lightEntity) {
        if (lightEntity == null || lightEntity.attributes() == null) {
            return LightKind.UNKNOWN;
        }

        LightEntity.Attributes attributes = lightEntity.attributes();

        if (attributes.memberEntityIds() != null && !attributes.memberEntityIds().isEmpty()) {
            return LightKind.GROUP;
        }

        Set<LightEntity.ColorMode> supportedModes = attributes.supportedColorModes();
        if (supportedModes != null && !supportedModes.isEmpty()) {
            if (supportedModes.size() == 1 && supportedModes.contains(LightEntity.ColorMode.onoff)) {
                return LightKind.ON_OFF_ONLY;
            }
            if (supportedModes.stream().anyMatch(mode -> mode != LightEntity.ColorMode.onoff)) {
                return LightKind.DIMMABLE_OR_COLOR;
            }
        }

        if (attributes.brightness() != null
            || attributes.colorTempKelvin() != null
            || attributes.hsColor() != null
            || attributes.rgbColor() != null
            || attributes.rgbwColor() != null
            || attributes.rgbwwColor() != null
            || attributes.xyColor() != null) {
            return LightKind.DIMMABLE_OR_COLOR;
        }

        return LightKind.ON_OFF_ONLY;
    }
}
