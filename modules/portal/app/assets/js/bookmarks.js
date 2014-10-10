$(function () {

  /*
   Bookmarks
   */

  function loadContents(itemId) {
    var func = jsRoutes.controllers.portal.Bookmarks.contents;
    func(itemId).ajax({
      success: function(data) {
        var $item = $("#bookmark-" + itemId);
        var $container = $("<div class=\"children\"></div>");
        if ($item.find(".children").length) {
          $container = $item.find(".children").first();
        }
        $container.html(data);
        $container.appendTo($item);
        initDraggables($container);
      }
    });
  }

  $(document).on("click", "a.bookmark-fetch-contents", function (e) {
    e.preventDefault();
    var $this = $(this);
    var $parent = $this.closest(".bookmark-item");

    if ($this.hasClass("closed")) {
      $this.removeClass("closed").addClass("expanded");
      loadContents($parent.data("id"));
    } else {
      $parent.find(".children").remove();
      $this.removeClass("expanded").addClass("closed");
    }
  });



  $(document).on("click", "a.bookmarks-fetchmore", function (e) {
    e.preventDefault();
    var $this = $(this);
    var $container = $this.parent();
    $.get(this.href, function (data) {
      $container.replaceWith(data);
      initDraggables($container);
    });
  });

  function initDraggables($element) {
    console.log("Element: ", $element.parent())
    var dropTargets = ".bookmark-item:not(#" + $element.parent().attr("id") + ")";
    console.log("Drop targets", dropTargets)
    $(".bookmark-list li", $element).draggable({
      revert: true,
      stack: ".bookmark-list li"
    }).droppable({
          hoverClass: "list-group-item-info",
          accept: dropTargets,
          drop: function (event, ui) {
            var $item = ui.draggable;
            var $dropped = $(this);
            var $parent = $item.parent().closest(".bookmark-item");
            if ($parent.length) {
              console.log("Moved:", $item.attr("id"), "from", $parent.attr("id"), "to", $dropped.attr("id"))
              var func = jsRoutes.controllers.portal.Bookmarks.moveBookmarksPost;
              func($parent.data("id"), $dropped.data("id"), [$item.data("id")]).ajax({
                success: function(data) {
                  console.log("data...", data)
                  loadContents($parent.data("id"));
                  loadContents($dropped.data("id"));
                }
              })
            }
          }
        });

  }

  initDraggables($(document));

});
