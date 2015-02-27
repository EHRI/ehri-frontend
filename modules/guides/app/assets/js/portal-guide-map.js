/* General functions */
function isNumeric(n) {
  return !isNaN(parseFloat(n)) && isFinite(n);
}
/* Default Params */
var mapParams = {
      lat: 50.508174054149,
      lng: 14.152353697237,
      zoom: 16
    },
    RedIcon = L.Icon.Default.extend({options: {iconUrl: redIcon_URL}}),
    redIcon = new RedIcon(),
    $map = L.map('map').setView([mapParams.lat, mapParams.lng], mapParams.zoom),
    $items = {},
    $bounds = [],
    $markers = {};

/*
 *
 *	Merging Params function
 *
 */

(window.onpopstate = function () {
  var match,
      pl = /\+/g,  // Regex for replacing addition symbol with a space
      search = /([^&=]+)=?([^&]*)/g,
      decode = function (s) {
        return decodeURIComponent(s.replace(pl, " "));
      },
      query = window.location.search.substring(1);

  while (match = search.exec(templateParams))
    if (isNumeric(match[2])) {
      mapParams[decode(match[1])] = parseFloat(decode(match[2]));
    } else {
      mapParams[decode(match[1])] = decode(match[2]);
    }

  while (match = search.exec(query))
    if (isNumeric(match[2])) {
      mapParams[decode(match[1])] = parseFloat(decode(match[2]));
    } else {
      mapParams[decode(match[1])] = decode(match[2]);
    }
})();


/*
 *
 *	MAP FUNCTIONS
 *
 */

function addMarker(data) {
  if (!$items[data.id]) {
    $items[data.id] = true;
    var desc = data.descriptions[0];
    var markerLocation = new L.LatLng(parseFloat(desc.latitude), parseFloat(desc.longitude));

    var markerProperty = {
      title: data.name,
      alt: data.name
    };
    if (ORIGINAL === true) {
      markerProperty.icon = redIcon;
    }

    $items[data.id] = L.marker(markerLocation, markerProperty).addTo($map).bindPopup('<b>' + data.name + '</b>', {
      minWidth: 300
    });
    return $items[data.id];
  }
  return false;
}

function addMarkerList(data) {
  var i = 0;
  while (data[i]) {
    if (data[i].descriptions.length == 1) {
      var desc = data[i].descriptions[0];
      if (desc.latitude !== null && desc.longitude !== null) {
        bindMarker(addMarker(data[i]), data[i].id, data[i].links)
      }
    }
    ++i;
  }
}

function panToPopup(elem) {
  var px = $map.project(elem.popup._latlng);
  px.y -= elem.popup._container.clientHeight / 2;
  $map.panTo($map.unproject(px), {animate: true});
}

function bindMarker(marker, id, linkCount) {
  var count = parseInt(linkCount);
  if (marker) {
    marker.on("popupopen", function (elem) {
      if (id in $markers) {
        $(elem.popup._contentNode).html($markers[id]);
        panToPopup(elem);
      } else {
        if (count > 0) {
          var html;
          $.get(jsRoutes.controllers.portal.guides.Guides.linkedDataInContext(id, VIRTUAL_UNIT).url, function (data) {
            var links = [];
            $.each(data, function (index, link) {
              links.push('<li><a href="' + jsRoutes.controllers.portal.guides.DocumentaryUnits.browse(GUIDE_PATH, link.id).url + '">' + link.name + '</a></li>')
            });
            html = $(elem.popup._contentNode).html();
            if (links.length) {
              html = html + '<ul class="list-unstyled">' + links.join(" ");
            }
            if (links.length == 5) {
              html = html + '<li><a href="' + VIRTUAL_UNIT_ROUTE + id + '"> ' + (count - 5) + ' More...</a></li>';
            }
            if (links.length) {
              html = html + '</ul>';
            }
            $markers[id] = html;
            $(elem.popup._contentNode).html(html);
            panToPopup(elem);
          });
        } else {
          $markers[id] = $(elem.popup._contentNode).html();
          panToPopup(elem);
        }
      }

    })
  }
}


/*
 Initiate Map
 */
L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {
  attribution: '&copy; <a href="http://osm.org/copyright">OpenStreetMap</a> contributors'
}).addTo($map);

function boundsToKm(bounds) {
  return (bounds._southWest.distanceTo(bounds._northEast) / 1000) / 2;
}

/*
 Movement loading and Maps Triggers
 */
$map.on('moveend', function (e) {
  $.get(MAP_URL, {
        lat: $map.getCenter().lat,
        lng: $map.getCenter().lng,
        d: boundsToKm($map.getBounds())
      },
      function (data) {
        addMarkerList(data.items);
      }, "json")
});

$(".zoom-to").on("click", function (e) {
  e.preventDefault();
  var $elem = $(this),
      $lng = $elem.attr("data-longitude"),
      $lat = $elem.attr("data-latitude"),
      $id = $elem.attr("data-id"),
      $markerLocation = new L.LatLng(parseFloat($lat), parseFloat($lng));
  $map.panTo($markerLocation);
  $items[$id].openPopup()
});


/* Initiate */
$(document).ready(function () {
  ORIGINAL = ("q" in mapParams && mapParams.q.length > 0 && mapParams.q != "undefined");

  $originalMarkers = $originalMarkers.items;
  addMarkerList($originalMarkers);
  $.each($originalMarkers, function (i, m) {
    var desc = m.descriptions[0];
    if (desc.latitude !== null && desc.longitude !== null) {
      $bounds.push([parseFloat(desc.latitude), parseFloat(desc.longitude)])
    }
  });
  if (ORIGINAL) $map.fitBounds($bounds);
  ORIGINAL = false;
});
