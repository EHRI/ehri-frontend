jQuery(function($) {
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
		$.get(jsRoutes.controllers.portal.guides.Guides.linkedDataInContext(e.attr("data-target"), VIRTUAL_UNIT).url,function(d){
			var links = []
			if(d.length > 0) {
				$.each(d, function(index, link) {
					links.push('<li><a href="'+ jsRoutes.controllers.portal.guides.DocumentaryUnits.browse(GUIDE_PATH, link.id).url +'">'+ link.name+'</a></li>')
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
