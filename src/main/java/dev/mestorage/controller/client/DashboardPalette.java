package dev.mestorage.controller.client;

record DashboardPalette(int background, int panel, int inset, int border, int text, int muted,
                        int accent, int selected, int hover, int slot, int danger, int warning) {
    static final DashboardPalette DARK = new DashboardPalette(0xff4c4f5d, 0xff4c4f5d, 0xff434658,
            0xff323544, 0xffdedeea, 0xffc5c7d7, 0xffafa0dc, 0xff656385, 0xff5d6072,
            0xff434658, 0xffff9c9c, 0xffebc278);
    static final DashboardPalette LIGHT = new DashboardPalette(0xffcbccd4, 0xffcbccd4, 0xff9a9fb4,
            0xff413f54, 0xff413f54, 0xff67667a, 0xff696d88, 0xfface9ff, 0xffd5e5ee,
            0xffadb0c4, 0xffce2401, 0xff916015);

    static DashboardPalette blend(float dark) {
        return new DashboardPalette(mix(LIGHT.background, DARK.background, dark), mix(LIGHT.panel, DARK.panel, dark),
                mix(LIGHT.inset, DARK.inset, dark), mix(LIGHT.border, DARK.border, dark),
                mix(LIGHT.text, DARK.text, dark), mix(LIGHT.muted, DARK.muted, dark),
                mix(LIGHT.accent, DARK.accent, dark), mix(LIGHT.selected, DARK.selected, dark),
                mix(LIGHT.hover, DARK.hover, dark), mix(LIGHT.slot, DARK.slot, dark),
                mix(LIGHT.danger, DARK.danger, dark), mix(LIGHT.warning, DARK.warning, dark));
    }

    static int mix(int from, int to, float progress) {
        float t = Math.max(0, Math.min(1, progress));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >>> shift & 255, b = to >>> shift & 255;
            result |= Math.round(a + (b - a) * t) << shift;
        }
        return result;
    }
}
