
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

  if (window.History && window.History.Adapter) {
    window.History.Adapter.bind(window, 'hashchange', collapseDescriptions);
  }

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
  var $select = $("select.select2");
  if ($select.select2 !== undefined) {
    $select.select2(select2Opts);
    $(document).ajaxComplete(function () {
      $("select.select2").select2(select2Opts);
    });
    var filterUrl = jsRoutes.controllers.portal.Portal.filterItems().url;

    $(".select2.item-filter").select2({
      minimumInputLength: 2,
      val: $(this).val(),
      initSelection: function(element, cb) {
        var value = $(element).val();
        if (!value) {
          cb(null);
        } else {
          var search = filterUrl + "?q=itemId:" + value;
          $.getJSON(search, function(data) {
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
  }


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
});

