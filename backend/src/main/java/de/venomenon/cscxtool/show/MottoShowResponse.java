package de.venomenon.cscxtool.show;

record MottoShowResponse(long id, int showNumber, String name) {

    static MottoShowResponse from(MottoShow show) {
        return new MottoShowResponse(show.id(), show.showNumber(), show.name());
    }
}
