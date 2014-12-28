
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

  var filterUrl = "/filter"; // FIXME: Use reverse routes

  $(".select2.item-filter").select2({
    minimumInputLength: 2,
    val: $(this).val(),
    initSelection: function(element, cb) {
      var value = $(element).val();
      if (!value) {
        cb(null);
      } else {
        $.getJSON(filterUrl + "?q=itemId:" + value, function(data) {
          if(data.items.length == 0) {
            cb({id: value, text: value});
          } else {
            cb({
              id: data.items[0].id,
              text: data.items[0].name
            });
          }
        });
      }
    },
    ajax: {
      url: filterUrl,
      dataType: "json",
      data: function(term, page ) {
        return {
          q: term,
          limit: 20,
          page: page,
          "st[]": $(this).data("entity-type")
        }
      },
      results: function(data, page) {
        return {
          results: data.items.map(function(value, idx) {
            console.log(data)
            return {
              id: value.id,
              text: value.name
            }
          })
        };
      }
    },
    formatResult: function(value) {
      return $("<div>" + value.text + "<span class='label label-primary pull-right'>" + value.id + "</span></div>");
    }
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
  $(".breadcrumb.collapsible").each(function(e) {
    var $ol = $(this),
        $width = $ol.outerWidth(),
        $li = $ol.find("li"),
        $padding = parseInt($li.outerWidth() - $li.width());

    if ($li.length !== "undefined" && $li.length > 1) {
      var $max = $width / $li.length;
      $max = $max - $padding;
      $max =  parseInt($max) - 1;
      $li.find("a:visible").css("max-width", $max);
      $li.data("max-width", $max);
    } else if($li.length !== "undefined" && $li.length == 1) {
       var $max = $width / $li.length;
            $max = $max - $padding;
            $max =  parseInt($max) - 1;
      $li.find("a:visible").css("max-width", $max);
      $li.data("max-width", $max).addClass("single");
    }
  });

  $(".breadcrumb.collapsible > li:not(.single)").hover(function() {
    //because some title could be SO LARGE, we have to compute what will be the end of the windows and make it stick a maximum to it...
    var $actual = $(this),
        $offset = $actual.offset(),
        $right = $offset.left,
        $top = $offset.top,
        $prev = $actual.prev("li"),
        $max = Math.max(document.documentElement["clientWidth"], document.body["offsetWidth"], document.documentElement["offsetWidth"]);


    if($prev.length !="undefined"&& $prev.length === 1) {
      $right = $prev.offset().left + $prev.outerWidth();
      $top = $prev.offset().top;
      console.log("prev exist")
    }

    $actual.next().css("margin-left", $actual.outerWidth())
    $actual.css({
      "top": $top,
      "left": $right,
      "position": "fixed",
      "z-index" : 9000
    });
    $actual.find("a:visible").css("max-width", "");

    if(!$actual.data("realwidth")) {
      $actual.data("realwidth", $actual.outerWidth())
    }

    if($actual.data("realwidth") + $right > $max) {
      $actual.css({
        "left" : $max - $actual.data("realwidth")
      });
    }

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
   */
});

