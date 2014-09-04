/* General functions */
function isNumeric(n) {
	return !isNaN(parseFloat(n)) && isFinite(n);
}
/* Default Params */
var	mapParams = {
		lat : 50.508174054149,
		lng : 14.152353697237,
		zoom : 13
	},
	RedIcon = L.Icon.Default.extend({options: { iconUrl: redIcon_URL} }),
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
		pl     = /\+/g,  // Regex for replacing addition symbol with a space
		search = /([^&=]+)=?([^&]*)/g,
		decode = function (s) { return decodeURIComponent(s.replace(pl, " ")); },
		query  = window.location.search.substring(1);

	while (match = search.exec(templateParams))
	   if(isNumeric(match[2])) { mapParams[decode(match[1])] = parseFloat(decode(match[2])); }
	   else { mapParams[decode(match[1])] = decode(match[2]); }

	while (match = search.exec(query))
	   if(isNumeric(match[2])) { mapParams[decode(match[1])] = parseFloat(decode(match[2])); }
	   else { mapParams[decode(match[1])] = decode(match[2]); }
})();


/*
 *
 *	MAP FUNCTIONS
 *
 */

excludeMarker = function() {
	var query = "",
	markers = [];

	$.each($items, function(i, e) {
		markers.push(i)
	});
	return markers;
	return $lastItems.slice(0,20);
}

addMarker = function(data) {
	if(!$items[data.id]) {
		$items[data.id] = true;
		var desc = data.descriptions[0];
		var markerLocation = new L.LatLng(parseFloat(desc.latitude),parseFloat(desc.longitude));

		var markerProperty = {
			title : data.name,
			alt : data.name
		};
		if(ORIGINAL === true) {
			markerProperty.icon = redIcon;
		}

		$items[data.id] = L.marker(markerLocation, markerProperty).addTo($map).bindPopup('<b>'+ data.name + '</b>', {
			minWidth : 300
		});
		return $items[data.id];
	}
	console.log(data.id + "already in there")
	return false;
}

addMarkerList = function(data) {
	var i = 0;
	$lastItems = [];
	while(data[i]) {
		if(data[i].descriptions.length == 1) {
			var desc = data[i].descriptions[0];
			if(desc.latitude !== null && desc.longitude !== null) {
			$lastItems.push(data[i].id);
				bindMarker(addMarker(data[i]), data[i].id, data[i].links)
			}
		}
		++i;
	}
}

panToPopup = function(elem)  {
	var px = $map.project(elem.popup._latlng);
	px.y -= elem.popup._container.clientHeight/2
	$map.panTo($map.unproject(px),{animate: true});
}

bindMarker = function(marker, id, linkCount) {
	var linkCount = parseInt(linkCount);
	if(marker) {
		marker.on("popupopen", function(elem) {
			if(id in $markers) {
				var html = $markers[id];
				$(elem.popup._contentNode).html(html);
				panToPopup(elem);
			} else {
				if(linkCount > 0) {
					$.get(jsRoutes.controllers.portal.Portal.linkedDataInContext(id , VIRTUAL_UNIT).url + "?type=cvocConcept", function(data) {
						var links = [];
						$.each(data, function(index, link) {
							links.push('<li><a href="'+ VIRTUAL_UNIT_ROUTE + link.id + '">'+ link.name+'</a></li>')
						})
						var html = $(elem.popup._contentNode).html();
						if(links.length > 0) { var html = html + '<ul class="list-unstyled">' + links.join(" "); }
						if(links.length > 0) { var html = html + '</ul>'; }

						html = html + '<p><small><a href="'+ VIRTUAL_UNIT_ROUTE+ id + '"> ' + linkCount + ' related documents...</a></small></p>';
						$markers[id] = html;
						$(elem.popup._contentNode).html(html)
						panToPopup(elem);
					});
				} else {
					var html = $(elem.popup._contentNode).html();
					$markers[id] = html;
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


/*
Movement loading and Maps Triggers
*/
$map.on('moveend', function(e) {
	$.post(MAP_URL, {exclude: excludeMarker(e), lat : $map.getCenter().lat, lng: $map.getCenter().lng }, 
		function (data) { 
			addMarkerList(data.items);
		}, "json")
});

$(".zoom-to").on("click", function(e) {
	e.preventDefault()
	var $elem = $(this),
		$lng = $elem.attr("data-longitude"),
		$lat = $elem.attr("data-latitude"),
		$id = $elem.attr("data-id"),
		$markerLocation = new L.LatLng(parseFloat($lat),parseFloat($lng));
	$map.panTo($markerLocation)
	$items[$id].openPopup()
})


/* Initiate */
$(document).ready(function() {
	ORIGINAL = false;
	if("q" in mapParams && mapParams.q.length > 0 && mapParams.q != "undefined") {
		ORIGINAL = true;
	}
	$originalMarkers = $originalMarkers.items;
	addMarkerList($originalMarkers)
	$.each($originalMarkers, function(i, m) {
		var desc = m.descriptions[0];
		if(desc.latitude !== null && desc.longitude !== null) {
			$bounds.push([parseFloat(desc.latitude), parseFloat(desc.longitude)])
		}
	});
	$map.fitBounds($bounds);
	ORIGINAL = false;
})
