
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

  /**
   * Select2 handling
   */
  window.select2Opts = {
    allowClear: true,
    dropdownAutoWidth: true,
    dropdownCssClass: "facet-select-dropdown",
    minimumInputLength: 0
  };


  // Re-check select2s whenever there's an Ajax event that could
  // load a widget (e.g. the profile form)
  $("select.select2").select2(select2Opts);
  $(document).ajaxComplete(function () {
    $("select.select2").select2(select2Opts);
  });


  // Handling form-submission via links, i.e. search form
  // when facets are clicked
  $(document).on("change", ".autosubmit", function (e) {
    $(e.target).closest("form").submit();
  });

  /*
   Search helpers
   */
  $(".page-content").on("click", ".search-helper-toggle", function () {
    $("#search-helper").toggle();
  }).on("click", "#search-helper .close", function(e) {
    e.preventDefault();
    $("#search-helper").toggle();
  });

  /*
   *   Breadcrumb and collapsible
   */
  $(".breadcrumb.collapsible").each(function(e) {
    var $ol = $(this);
    var $width = $ol.width();
    var $li = $ol.find("li");
    var $padding = parseInt($li.outerWidth() - $li.width())
    if ($li.length !== "undefined" && $li.length > 0) {
      var $max = $width / $li.length;
      $max = $max - $padding;
      $li.find("a:visible").css("max-width", $max);
      $li.data("max-width", $max);
    }
  });

  $(".breadcrumb.collapsible > li").hover(function() {
    var $actual = $(this);
    var $offset = $actual.offset();
    var $right = $offset.left;
    var $top = $offset.top;
    var $prev = $actual.prev();
    if($prev.length !="undefined"&& $prev.length === 1) {
      $right = $prev.offset().left + $prev.outerWidth();
      $top = $prev.offset().top;
    }
    $actual.next().css("margin-left", $actual.outerWidth())
    $actual.css({
      "top": $top,
      "left": $right,
      "position": "fixed",
      "z-index" : 9000
    });
    $actual.find("a:visible").css("max-width", "");

  } , function() {
    var $actual = $(this);
    $actual.next().css("margin-left", 0);
    $actual.before().css("z-index", "");
    $actual.css({
      "top": 0,
      "left": 0,
      "position": "relative",
      "z-index" : ""
    });
    $actual.find("a:visible").css("max-width", $actual.data("max-width"));
  });
});

