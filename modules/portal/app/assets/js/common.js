
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
        && descId !== FB_REDIRECT_HASH
        && $(descId).hasClass("description-holder");
  }

  setTimeout(function() {
    if (isDescriptionRef(location.hash)) {
      window.scrollTo(0, 0);
    }
  }, 0);

  $(document).on("click", ".description-switch", function(e) {
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

  $(document).on("description.change", collapseDescriptions);

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
            if(data.items.length === 0) {
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

  // Inline tree navigation
  // Add inline load class to all child-count items
  function addInlineLoadLinks(scope) {
    $(".child-count > a", scope)
        .addClass("child-items-inline-load collapsed")
        .map(function () {
          $(this)
              .attr("href", this.href.replace(/(\?inline=true)?$/, "?inline=true"));
        });
  }

  addInlineLoadLinks(document);

  // remove inline lists when the [-] is clicked
  $(document).on("click", "a.child-items-inline-load.expanded", function(e) {
    e.preventDefault();
    var $self = $(this);
    $self.parent().find("> .child-items-inline").remove();
    $self.toggleClass("expanded collapsed");
  });

  // load inline lists when the [+] is clicked
  $(document).on("click", "a.child-items-inline-load.collapsed", function(e) {
    e.preventDefault();
    var $self = $(this),
        url = this.href;
    $self.addClass("disabled loading").removeAttr("href");
    $.get(url, function(data, _, res) {
      var more = res.getResponseHeader("more") === true.toString();
      var $data = $.parseHTML(data, false);
      addInlineLoadLinks($data);
      $self.parent().append($data);
      $self.attr("href", url);
      $self.toggleClass("expanded collapsed disabled loading");
    })
  });

  // load more content in a long list
  $(document).on("click", "a.child-items-inline-list-more", function(e) {
    e.preventDefault();
    var $self = $(this),
        url = this.href;
    $self.addClass("loading").removeAttr("href");
    $.get(url, function(data, _, res) {
      var more = res.getResponseHeader("more") === true.toString();
      var $items = $(".child-items-inline-list > li", $.parseHTML(data, false));
      addInlineLoadLinks($items);
      $self.removeClass("loading").attr("href", url);
      $self.parent().find("> .child-items-inline-list").append($items);
      $self.attr("href", url.replace(/(page=)(-?\d+)/, function(match, param, val) {
        return param + (parseInt(val) +  1);
      }));
      if (!more) {
        $self.hide();
      }
    })
  });

  // Toggle text content on expander buttons
  // for long facet lists
  $(".more-less-options").click(function(e) {
    e.preventDefault();
    var $this = $(this),
        $target = $($this.attr("href"));
    var txt = $target.is(':visible') ? 'More...' : 'Less...';
    $this.text(txt);
    $target.slideToggle();
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

  /**
   * Quick and dirty way of Ajax-ing a link which
   * directs to an empty submit form. Note: this
   * does not work well for deletes because it simply
   * refreshes the page after success (and in that case
   * the item will have been deleted, resulting in a 404).
   */
  $("a.ajax-action").click(function(e) {
    var $link = $(this),
        href = $link.attr("href"),
        check = $link.attr("title");
    e.preventDefault();
    if (confirm(check)) {
      $.post(href, function(data) {
        location.reload();
      })
    }
  });

  /**
   * Handle cookie pref loading/saving
   */
  window.Preferences = {
    update: function(prefsObj) {
      var prefs = prefsObj || {};
      jsRoutes.controllers.portal.Portal.updatePrefs().ajax({
        data: prefsObj
      }).error(function(err) {
        console.error("Unable to update preferences: ", err);
      });
    },

    updateValue: function(key, value) {
      var tmp = {};
      tmp[key] = value;
      return this.update(tmp);
    }
  };

  /**
   * Handle updating global preferences when certain
   * items are clicked.
   */
  $(document).on("click", ".toggle-boolean-preference", function(e) {
    e.preventDefault();
    e.stopPropagation();
    var $item = $(this),
        name = $item.data("preference-name"),
        value = $item.data("preference-value");
    $item
        .addClass("boolean-" + !value).removeClass("boolean-" + value)
        .data("preference-value", !value);
    Preferences.updateValue(name, !value);
    $(window.Preferences).trigger(name, !value);
  });

  /**
   * Preference events
   */
  $(window.Preferences).bind("showUserContent", function(event, doShow) {
    if (doShow) {
      $(".user-content").removeClass("hidden");
      $(".hidden-toggle").addClass("fa-comments").removeClass("fa-comments-o")
    } else {
      $(".user-content").addClass("hidden");
      $(".hidden-toggle").addClass("fa-comments-o").removeClass("fa-comments")
    }
  });

  $(document).on("click", ".hidden-toggle", function(e) {
    e.preventDefault();
    var $item = $(e.target);
    $item.closest(".item-text-field").find(".annotation-list > .user-content")
        .toggleClass("hidden");
    $item.toggleClass("fa-comments fa-comments-o")
  });
});

