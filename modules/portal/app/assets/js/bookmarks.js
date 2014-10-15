window.BS = {
  /*
   Bookmarks. This is really gnarly.
   */

  /**
   * Load the contents of a bookmark set
   * @param id The set ID
   */
  _getContents: jsRoutes.controllers.portal.Bookmarks.contents,

  /**
   * Move bookmarks between sets
   * @param fromId From set ID
   * @param toId  To set ID
   * @param moveIds List of IDs to move
   */
  _moveBookmarks: jsRoutes.controllers.portal.Bookmarks.moveBookmarksPost,

  /**
   * Filter a list of items selecting only the virtual units.
   * @param $items A set of virtual or non-virtual items
   * @returns {*}
   */
  virtualUnits: function($items) {
    return $items.filter(".bookmark-item.virtualUnit");
  },

  /**
   * Filter a list of items selecting only the non-virtual ones.
   * @param $items A set of virtual or non-virtual items
   * @returns {*}
   */
  documentaryUnits: function($items) {
    return $items.filter(".bookmark-item.documentaryUnit");
  },

  idOf: function($item) {
    return $item.data("id");
  },


  /**
   * Fetch the container for the child items
   * @param $item The item jQ object
   * @returns The child container jQ object
   */
  childrenOf: function($item) {
    return $item.children("ul").first();
  },

  removeItem: function($parent) {
    var $counter = $parent
        .children(".bookmark-data")
        .children(".item-count");
    var currentCount = parseInt($counter.html());
    if (isNaN(currentCount)) {
      currentCount = 0;
    }
    var newCount = Math.max(0, currentCount - 1);
    $counter.html(newCount);
    if (newCount == 0) {
      $counter.hide()
    } else {
      $counter.show();
    }
  },

  addItem: function($parent) {
    var $counter = $parent
        .children(".bookmark-data")
        .children(".item-count");
    var currentCount = parseInt($counter.html());
    if (isNaN(currentCount)) {
      currentCount = 0;
    }
    var newCount = currentCount + 1;
    $counter.html(newCount);
    if (newCount == 0) {
      $counter.hide()
    } else {
      $counter.show();
    }
  },

  /**
   * Filter a list of items selecting only the virtual units.
   * @param $item A set of virtual or non-virtual items
   * @returns {*}
   */
  childItems: function($item) {
    return $("ul > li.bookmark-item", $item);
  },

  hasLoadedChildren: function($item) {
    return BS.childItems($item).length > 0;
  },

  moveItem: function($from, $to, $item) {
    return BS._moveBookmarks(
        BS.idOf($from),
        BS.idOf($to),
        [BS.idOf($item)]
    ).ajax().then(function() {
        return BS.removeItem($from);
    }).then(function() {
        return BS.loadChildren($from);
    }).then(function() {
        return BS.addItem($to);
    }).then(function() {
        return BS.loadChildren($to);
    });
  },

  loadChildren: function($item) {
    return BS._getContents(BS.idOf($item)).ajax()
        .then(function(data) {
          BS.hasLoadedChildren($item)
              ? BS.childrenOf($item).replaceWith(data)
              : $item.append(data);
          console.log("Loaded children for: ", BS.idOf($item));
        });
  }
};

jQuery(function ($) {

  function loadContents(itemId) {
    var func = jsRoutes.controllers.portal.Bookmarks.contents;
    func(itemId).ajax({
      success: function(data) {
        var $item = $("#bookmark-" + itemId);
        $item
            .children("ul.bookmark-list").remove();
        $item.append(data);
        initDraggables($item);
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
      $parent.find("ul.bookmark-list").remove();
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

  function modCount($item, modFunc) {
    var $count = $item.children(".bookmark-data").children(".item-count");
    var num = isNaN(parseInt($count.html())) ? 0 : parseInt($count.html());
    $count.html(modFunc(num));
  }

  function initDraggables($element) {

    console.log("Init draggables for: ", $element.attr("id"));

    //var dropTargets = "li.bookmark-item.virtualUnit:not(#" + $element.attr("id") + ")";
    var dropTargets = ":not(#" + $element.attr("id") + " > ul > li)";
    console.log("Drop targets", dropTargets)

    var dropOpts = {
      hoverClass: "list-group-item-info",
      accept: dropTargets,
      drop: function (event, ui) {
        var $item = ui.draggable;
        var $dropped = $(this);
        var $parent = $item.parent().closest(".bookmark-item");
        if ($parent.length) {
          console.log("Moved:", $item.attr("id"), "from", $parent.attr("id"), "to", $dropped.attr("id"))
          if ($parent.attr("id") != $dropped.attr("id")) {
            BS.moveItem($parent, $dropped, $item)
                .then(function() {
                console.log("Item moved!");
                  initDraggables($parent);
                  initDraggables($dropped);
            });
          }
        }
      }
    };

    console.log("Element: ", $element)

    $(".bookmark-list li.droppable", $element).droppable(dropOpts);

    var dragOpts = {
      handle: ".drag-handle",
      revert: true,
      stack: ".bookmark-list .bookmark-item",
      start: function() {
        $(this).addClass("dragging")
      },
      stop: function() {
        $(this).removeClass("dragging");
      }
    };

    $(".bookmark-list li.virtualUnit.moveable", $element).draggable(dragOpts);
    $(".bookmark-list li.documentaryUnit.moveable", $element).draggable(dragOpts);
  }

  initDraggables($(document));

});
