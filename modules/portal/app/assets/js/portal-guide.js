$(document).ready(function() {
	var resetPopover = function(elem) {
		$("#main .popover-accesspoints").not(elem).each(function() {
			var e = $(this);
			if(e.data("loaded")) {
				e.popover("hide");
			}
		})
	}
	$("#main").on('click', ".popover-accesspoints", function() {
		var e=$(this),
			that = this;
		if(e.data("loaded")) {
			return true;
		}
		e.data("loaded", true)

		e.unbind('click');
		e.css("cursor", "wait");
		$.get(jsRoutes.controllers.portal.Portal.linkedDataInContext(e.attr("data-target"), VIRTUAL_UNIT).url,function(d){
			var links = []
			if(d.length > 0) {
				$.each(d, function(index, link) {
					links.push('<li><a href="'+ jsRoutes.controllers.portal.Guides.browseDocument(GUIDE_PATH, link.id).url +'">'+ link.name+'</a></li>')
				})

				var html = '<small><ul class="list-unstyled">' + links.join(" ") + '<li>';
				if(links.length == 5) { var html = html + '<a href="'+ VIRTUAL_UNIT_ROUTE + e.attr("data-target") + '">' + (parseInt(e.attr("data-count")) - d.length) + ' more ...</a></li>'; }
				var html = html + '</ul></small>';
				e.popover({content: html, html: true, trigger: "click", container : "body"}).popover('show');
				resetPopover(that)
				e.on("click", function() { resetPopover(this); });
				e.css("cursor", "pointer");
			} else {

				if(e.find(".glyphicon-search").length > 0) {
					e.hide();
				}
				e.css("cursor", "pointer");
			}
		});
	});
})

jQuery(function($) {

  var FB_REDIRECT_HASH = "#_=_";
/*
 * Description viewport code. This fixes a viewport to a list
* of item descriptions so only the selected one is present
* at any time.
*/

// HACK! If there's a description viewport, disable jumping
// to the element on page load... this is soooo horrible.

/**
* Determine if the fragment refers to a description element.
*/
function isDescriptionRef(descId) {
// NB: The _=_ is what Facebook adds to Oauth login redirects
return descId
    && descId != FB_REDIRECT_HASH
    && $(descId).hasClass("description-holder");
}

setTimeout(function() {
if (isDescriptionRef(location.hash)) {
  window.scrollTo(0, 0);
}
}, 0);

$(".description-switch").click(function(e) {
	e.preventDefault();
	var descId = "#desc-" + $(this).data("id");
	location.hash = descId;
	switchDescription(descId);
});

function switchDescription(descId) {
$(".description-viewport").each(function(i, elem) {

  var $vp = $(elem);
  var $descs = $vp.find(".description-holder");

  // If the hash isn't set, default to the first element
  if (!descId) {
    descId = "#" + $descs.first().attr("id");
  }

  var $theitem = $(descId, $vp);

  $theitem.show();

  $descs.not($theitem).hide();

  // Set the active class on the current description
  $(".description-switch[href='" + descId + "']").addClass("active")
  $(".description-switch[href!='" + descId + "']").removeClass("active")
});

}

function collapseDescriptions() {
if (isDescriptionRef(location.hash)) {
  switchDescription(location.hash);
} else {
  switchDescription();
}
}

History.Adapter.bind(window, 'hashchange', collapseDescriptions);

// Trigger a change on initial load...
collapseDescriptions();
})