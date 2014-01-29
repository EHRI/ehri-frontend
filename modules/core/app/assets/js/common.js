jQuery(function($) {

  var FB_REDIRECT_HASH = "#_=_";

  /**
   * Description viewport code. This fixes a viewport to a list
   * of item descriptions so only the selected one is present
   * at any time.
   */

    // HACK! If there's a description viewport, disable jumping
    // to the element on page load... this is soooo horrible.

  /**
   * Determine if the fragment refers to a description element.
   */
  function isDescriptionRef() {
    // NB: The _=_ is what Facebook adds to Oauth login redirects
    return location.hash
        && location.hash != FB_REDIRECT_HASH
        && $(location.hash).hasClass("description-holder");
  }

  setTimeout(function() {
    if (isDescriptionRef()) {
      window.scrollTo(0, 0);
    }
  }, 0);

  function collapseDescriptions() {
    if (!isDescriptionRef()) {
      return;
    }

    var hash = location.hash;

    $(".description-viewport").each(function(i, elem) {

      var $vp = $(elem);
      var $descs = $vp.find(".description-holder");

      // If the hash isn't set, default to the first element
      if (!hash) {
        hash = "#" + $descs.first().attr("id");
      }

      var $theitem = $(hash, $vp);

      $theitem.show();
      $descs.not($theitem).hide();

      // Set the active class on the current description
      $(".description-switch[href='" + hash + "']").parent().addClass("active")
      $(".description-switch[href!='" + hash + "']").parent().removeClass("active")
    });
  }

  History.Adapter.bind(window, 'hashchange', collapseDescriptions);

  // Trigger a change on initial load...
  collapseDescriptions();
});

