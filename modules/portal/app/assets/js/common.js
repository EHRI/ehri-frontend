
jQuery(function($) {

  // https://stackoverflow.com/a/20420424/285374
  function replaceUrlParam(url, paramName, paramValue) {
    if (paramValue == null) {
      paramValue = '';
    }
    var pattern = new RegExp('\\b(' + paramName + '=).*?(&|#|$)');
    if (url.search(pattern) >= 0) {
      return url.replace(pattern, '$1' + paramValue + '$2');
    }
    url = url.replace(/[?#]$/, '');
    return url + (url.indexOf('?') > 0 ? '&' : '?') + paramName + '=' + paramValue;
  }

  /**
   * Description viewport code. This fixes a viewport to a list
   * of item descriptions so only the selected one is present
   * at any time.
   */

  /**
   * Determine if the fragment refers to a description element.
   */
  function isDescriptionRef(descId) {
    // NB: The _=_ is what Facebook adds to Oauth login redirects
    return descId.length > 6
        && descId.substr(0, 6) === "#desc-"
        && $(descId).hasClass("description-holder");
  }

  // Backwards compatibility with old hash-based description
  // targeting
  if (isDescriptionRef(location.hash)) {
    var descId = location.hash;
    location.hash = "";
    switchDescription(descId.substr(6));
  }

  // Switch description when user clicks on side panel
  $(document).on("click", ".description-switcher a", function(e) {
    e.preventDefault();
    switchDescription($(this).data("id"));
  });

  function switchDescription(descId) {
    if (!descId) return;

    $(".description-viewport").each(function(i, elem) {

      var $vp = $(elem);
      var $descs = $vp.find(".description-holder");
      var oldUrl = location.pathname + location.search + location.hash;
      var newUrl = replaceUrlParam(oldUrl, "dlid", descId);

      var $theitem = $('#desc-' + descId, $vp);
      if ($theitem.length > 0) {
        $theitem.show();
        $descs.not($theitem).hide();
      }

      // Set the active class on the current description
      var $active = $(".description-switcher a[data-id='" + descId + "']"),
          $inactive = $(".description-switcher a[data-id!='" + descId + "']");

      $active.addClass("active");
      $inactive.removeClass("active");

      // Set the label if we're using a dropdown..
      $("#description-switcher-toggle > label").text($active.text());

      // Swap URL and attributes referring to the old URL...
      history.replaceState({}, "Description", newUrl);
      $("form[action='" + oldUrl + "']").attr("action", newUrl);
      $("a[href='" + oldUrl + "']").attr("href", newUrl);
      $("input[name='dlid']").val(descId);
    });

  }

  /**
   * Select2 handling
   */
  window.select2Opts = {
    theme: "bootstrap4",
    dropdownAutoWidth: true,
    dropdownCssClass: "facet-select-dropdown",
    minimumInputLength: 0
  };

  // Re-check select2s whenever there's an Ajax event that could
  // load a widget (e.g. the profile form)
  var $select = $("select.select2:visible");
  if ($select.select2 !== undefined) {
    $select.select2(select2Opts);
    $(document).ajaxComplete(function () {
      $("select.select2:visible").select2(select2Opts);
    });
  }


  // Handling form-submission via links, i.e. search form
  // when facets are clicked
  $(document).on("change", ".autosubmit", function (e) {
    $(e.target).closest("form").submit();
  });

  // Inline tree navigation
  // Add inline load class to all item-children items
  function addInlineLoadLinks(scope) {
    if (window.URLSearchParams) {
      $(".item-children > a.child-items-inline-load.collapsed", scope)
        .map(function () {
          var url = new URL(this.href),
            params = new URLSearchParams(url.search);
          params.set("inline", true)
          url.search = params.toString();
          $(this).attr("href", url.toString());
        });
    }
  }

  addInlineLoadLinks(document);
  $(document).ajaxComplete(function () {
    addInlineLoadLinks(document);
  });

  // remove inline lists when the [-] is clicked
  $(document).on("click", "a.child-items-inline-load.expanded", function(e) {
    e.preventDefault();
    var $self = $(this);
    $self.parent().find("> .child-items-inline").remove();
    $self.toggleClass("expanded collapsed")
        .find(".fa").toggleClass("fa-plus-square-o fa-minus-square-o");
  });

  // load inline lists when the [+] is clicked
  $(document).on("click", "a.child-items-inline-load.collapsed", function(e) {
    e.preventDefault();
    var $self = $(this),
        url = this.href;
    $self.addClass("disabled loading").removeAttr("href")
      .find(".fa")
      .removeClass("fa-plus-square-o")
      .addClass("fa-circle-o-notch fa-spin");
    $.get(url, function(data, _, res) {
      var more = res.getResponseHeader("more") === true.toString();
      var $data = $.parseHTML(data, false);
      addInlineLoadLinks($data);
      $self.parent().append($data);
      $self.attr("href", url);
      $self.toggleClass("expanded collapsed disabled loading")
        .find(".fa")
        .toggleClass("fa-circle-o-notch fa-spin fa-minus-square-o");
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

  // load more content in a long list 2 FIXME DUP
  $(document).on("click", "a.search-item-list-more", function(e) {
    e.preventDefault();
    var $self = $(this),
        url = this.href;
    $self.addClass("loading").removeAttr("href");
    $.get(url, function(data, _, res) {
      var more = res.getResponseHeader("more") === true.toString();
      var $items = $("> li", $.parseHTML(data, false));
      addInlineLoadLinks($items);
      $self.removeClass("loading").attr("href", url);
      $self.parent().find("> .search-result-list").append($items);
      // TODO: does changing history make sense here?
      //history.replaceState({}, "Next page", url);
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
  $(".search-bar").on("click", ".search-helper-toggle", function () {
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
      }).fail(function(err) {
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

