jQuery(function($) {
    "use strict";

    $(".point-map").each(function(e) {
        var $elem = $(this),
            lat = $elem.data("lat"),
            lon = $elem.data("lon"),
            name = $elem.data("point");

        var map = L.map(this).setView([lat, lon], 6);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(map);

        L.marker([lat, lon])
            .addTo(map)
            .bindPopup(name)
            .openPopup();
    });
});
