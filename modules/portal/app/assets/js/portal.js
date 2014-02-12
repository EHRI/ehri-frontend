jQuery(function ($) {

  $(".content-load a.toggle").click(function(e){
    e.preventDefault();
    var $link = $(this)
        $text = $(".text", $link),
        $inverse = $link.data("inverse-text");
    var $container = $link.parent(),
        $data = $(".content-load-data", $container);
    if ($container.hasClass("loaded")) {
      $data.toggle(300)
      $link.data("inverse-text", $text.text())
      $text.text($inverse)
    } else {
      $link.addClass("loading");
      $.get(this.href, function(data) {
        $data.append(data).show(300)
        $container.addClass("loaded")
        $link.removeClass("loading")
        $link.data("inverse-text", $text.text())
        $text.text($inverse)
      }, "html")
    }
  });


  $(".content-load a.load-in-view").on("visible", function(e){
    var $link = $(this),
        $container = $link.parent(),
        $data = $(".content-load-data", $container);
    $link.addClass("loading");
    $data.load(this.href, function() {
      $data.find("select").each(function(i) {
        $(this).select2(select2Opts);
        $link.hide();
      });
    })
  });

  function checkLoadVisibility() {
    $(".load-in-view").not(".loading").each(function(i) {
      var $item = $(this);
      if(!$item.hasClass("loading")) {
        if (($(window).scrollTop() + $(window).height()) > $item.offset().top) {
          $item.trigger("visible")
        }
      }
    });
  }

  checkLoadVisibility()
  $(window).scroll(function(e) {
    checkLoadVisibility()
  });


  // Make global search box show up when focused...
  // This could be done with plain CSS if we didn't also
  // want to toggle the color of the search icon...
  $(".global-search input").focusin(function() {
    $(this).parent().removeClass("inactive");
  }).focusout(function() {
    $(this).parent().addClass("inactive");
  });

  // Make top menu adhere to top of screen...
  var $pmenu = $(".nav-primary");
  var $smenu = $(".nav-secondary");
  var menuHeight = $smenu.height();
  $(window).scroll(function(e) {

    if ($(window).scrollTop() > ($pmenu.offset().top + $pmenu.height() + menuHeight)) {
      $smenu.addClass("float-nav").css({
        width: $(window).width()
      })
    } else {
      $smenu.removeClass("float-nav").css("width", $pmenu.outerWidth());
    }
  });

  // jQuery history plugin... initialise
  // Bind to StateChange Event
  History.Adapter.bind(window,'statechange',function(){ // Note: We are using statechange instead of popstate
    var State = History.getState(); // Note: We are using History.getState() instead of event.state

    // TODO: Use...
  });

  // Integrate Bootstrap tab switching with the history plugin...
  $('a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
    // e.target // activated tab
    // e.relatedTarget // previous tab
    var t = $(e.target).data("tabidx"); // chop off #
    History.replaceState({tabState: t}, t, "?tab=" + t);
  });

  // Validate any forms with 'validate-form' class...
  $(".validate-form").validate();
  $(document).ajaxComplete(function () {
    $(".validate-form").validate();
    $("textarea.autosize").autosize();
  });


  var select2Opts = {
    placeholder: "Select an option...",
    allowClear: true,
    dropdownAutoWidth: true,
    dropdownCssClass: "facet-select-dropdown",
    minimumInputLength: 0
  };

  // Re-check select2s whenever there's an Ajax event that could
  // load a widget (e.g. the profile form)
  $(document).ajaxComplete(function () {
    //$(".select2").select2(select2Opts);
  });

  $(".select2").select2(select2Opts).change(function (e) {
    if ($(e.target).hasClass("autosubmit")) {
      $(e.target).closest("form").submit();
    }
  });

  $(".facet-toggle").change(function (e) {
    $(e.target).closest("form").submit();
  });

});

/**
 * Activity-related functions
 */
jQuery(function ($) {

  // Fetch more activity...
  $("#activity-stream-fetchmore").click(function (event) {
    var offset = $(event.target).data("offset");
    var limit = $(event.target).data("limit")
    jsRoutes.controllers.portal.Portal.personalisedActivityMore(offset).ajax({
      success: function (data) {
        console.log("Data", data);
        $("#activity-stream").append(data);
        $(event.target).data("offset", offset + limit);
      }
    });
  });

  /**
   * Handler following/unfollowing users via Ajax.
   */
  $(document).on("click", "a.follow, a.unfollow", function (e) {
    e.preventDefault();

    var followFunc = jsRoutes.controllers.portal.Portal.followUserPost,
        unfollowFunc = jsRoutes.controllers.portal.Portal.unfollowUserPost,
        followerListFunc = jsRoutes.controllers.portal.Portal.followersForUser,
        $elem = $(e.target),
        id = $elem.data("item"),
        follow = $elem.hasClass("follow");

    var call, $other;
    if (follow) {
      call = followFunc;
      $other = $elem.parent().find("a.unfollow");
    } else {
      call = unfollowFunc;
      $other = $elem.parent().find("a.follow");
    }

    call(id).ajax({
      success: function () {
        // Swap the buttons and, if necessary, reload
        // their followers list...
        $elem.hide();
        $other.show();
        $(".browse-users-followers")
            .load(followerListFunc(id).url);

        // If a follower count is shown, munge it...
        var fc = $(".user-follower-count");
        if (fc.size()) {
          var cnt = parseInt(fc.html(), 10);
          fc.html(follow ? (cnt + 1) : (cnt - 1));
        }
      }
    });
  });

  /**
   * Handle watching/unwatching items using Ajax...
   */
  $(document).on("click", "a.watch, a.unwatch", function (e) {
    e.preventDefault();

    var watchFunc = jsRoutes.controllers.portal.Portal.watchItemPost,
        unwatchFunc = jsRoutes.controllers.portal.Portal.unwatchItemPost,
        $elem = $(e.target),
        id = $elem.data("item"),
        watch = $elem.hasClass("watch");

    var call, $other;
    if (watch) {
      call = watchFunc;
      $other = $elem.parent().find("a.unwatch");
    } else {
      call = unwatchFunc;
      $other = $elem.parent().find("a.watch");
    }

    call(id).ajax({
      success: function () {
        // Swap the buttons and, if necessary, reload
        // their followers list...
        $elem.hide();
        $other.show();

        // If a watch count is shown, munge it...
        var fc = $(".item-watch-count");
        if (fc.size()) {
          var cnt = parseInt(fc.html(), 10);
          fc.html(watch ? (cnt + 1) : (cnt - 1));
        }
      }
    });
  });
});

/**
 * Handle cookie pref loading/saving
 */
jQuery(function ($) {
  // Default pref path
  var cookieName = "userPrefs";

  // Default structure: this should match `SessionPrefs.scala`
  var defaultPrefs = {
    showUserContent: true
  };

  window.Preferences = {
    update: function(prefsObj) {
      var prefs = prefsObj || {};
      jsRoutes.controllers.portal.Portal.updatePrefs().ajax({
        data: prefsObj,
        success: function(d) {
          console.log(d)
        }
      })
    },
    updateValue: function(key, value) {
      // fffff...
      var tmp = {};
      tmp[key] = value;
      return this.update(tmp);
    }
  };
});

/**
 * Handle updating global preferences when certain
 * items are clicked.
 */
jQuery(function($) {
  $(document).on("click", ".toggle-user-preference", function(e) {
    var $item = $(this),
        name = $item.data("preference-name"),
        value = $item.data("preference-value");
    Preferences.updateValue(name, !value);
    $item.data("preference-value", !value);
    $(window.Preferences).trigger(name, !value);
  })
});

/**
 * Preference events
 */
jQuery(function($) {
  $(window.Preferences).bind("showUserContent", function(event, doShow) {
    $(".user-content").toggle(doShow);
  })
});

/**
 * Annotation-related functions
 */
jQuery(function ($) {

  // Show/hide hidden annotations...
  $(".show-other-annotations").click(function(event) {
    event.preventDefault();
    $(this).find("span")
        .toggleClass("glyphicon-chevron-up")
        .toggleClass("glyphicon-chevron-down")
      .end()
        .closest(".item-text-field-annotations, .description-annotations")
        .find(".other").toggle();
  });

  function insertAnnotationForm($elem, data) {
    $elem.hide().parent().after(data);
    $(data).find("select.custom-accessors").select2({
      placeholder: "Select a set of groups or users",
      width: "copy"
    });
  }

  // Load an annotation form...
  $(document).on("click", ".annotate-item", function(e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item"),
        did = $elem.data("did");
    jsRoutes.controllers.portal.Portal.annotate(id, did).ajax({
      success: function (data) {
        insertAnnotationForm($elem, data)
      }
    });
  });

  // Fields are very similar but we have to use the field as part
  // of the form submission url...
  $(document).on("click", ".annotate-field", function(e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item"),
        did = $elem.data("did"),
        field = $elem.data("field");
    jsRoutes.controllers.portal.Portal.annotateField(id, did, field).ajax({
      success: function (data) {
        insertAnnotationForm($elem, data)
      }
    });
  });


  // POST back an annotation form and then replace it with the returned
  // data.
  $(document).on("submit", ".annotate-item-form", function(e) {
    e.preventDefault();
    var $form = $(this);
    var action = $form.attr("action");
    $.ajax({
      url: action,
      data: $form.serialize(),
      method: "POST",
      success: function(data) {
        $form.prev().find(".annotate-field, .annotate-item").show()
        $form.parents(".annotation-set").find("ul").append(data);
        $form.remove();
      }
    });
  });

  // Fields are very similar but we have to use the field as part
  // of the form submission url...
  $(document).on("click", ".edit-annotation", function(e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item");
    jsRoutes.controllers.portal.Portal.editAnnotation(id).ajax({
      success: function(data) {
        //$elem.closest(".annotation").hide().after(data)
        $(data)
          .insertAfter($elem.closest(".annotation").hide())
          .find("select.custom-accessors").select2({
            placeholder: "Select a set of groups or users",
            width: "copy"
          });
      }
    });
  });

  $(document).on("click", ".edit-annotation-form .close", function(e) {
    e.preventDefault();
    var $form = $(e.target).parents(".edit-annotation-form");
    var hasData = $("textarea[name='body']", $form).val().trim() !== "";
    if (!hasData || confirm("Discard comment?")) {
      $form.prev(".annotation").show();
      $form.remove()
    }
  });

  $(document).on("click", ".annotate-item-form .close", function(e) {
    e.preventDefault();
    var $form = $(e.target).parents(".annotate-item-form");
    var hasData = $("textarea[name='body']", $form).val().trim() !== "";
    if (!hasData || confirm("Discard comment?")) {
      $form.prev().find(".annotate-field, .annotate-item").show();
      $form.remove()
    }
  });

  // POST back an annotation form and then replace it with the returned
  // data.
  $(document).on("submit", ".edit-annotation-form", function(e) {
    e.preventDefault();
    var $form = $(this);
    var action = $form.closest("form").attr("action");
    $.ajax({
      url: action,
      data: $form.serialize(),
      method: "POST",
      success: function(data) {
        $form.next(".annotate-field").show()
        $form.replaceWith(data);
      }
    });
  });

  // Fields are very similar but we have to use the field as part
  // of the form submission url...
  $(document).on("click", ".delete-annotation", function(e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item");
    if (confirm("Delete annotation?")) { // FIXME: i18n?
      var $ann = $elem.closest(".annotation");
      $ann.hide();
      jsRoutes.controllers.portal.Portal.deleteAnnotationPost(id).ajax({
        success: function(data) {
          $ann.remove();
        },
        error: function() {
          $ann.show();
        }
      });
    }
  });

  $(document).on("change", "input[type=radio].visibility", function(e) {
    $(".custom-visibility").toggle(e.target.value === "custom")
    $(".custom-visibility").find("select.custom-accessors").select2({
      placeholder: "Select a set of groups or users",
      width: "copy"
    });
  });

  // Set visibility of annotations
  // POST back an annotation form and then replace it with the returned
  // data.
  $(document).on("change", ".edit-annotation-form .visibility, .edit-annotation-form .custom-accessors", function(e) {
    e.preventDefault();
    // Toggle the accessors list
    var $form = $(this).closest("form"),
      id = $form.prev(".annotation").attr("id"),
      data = $form.serialize();
    jsRoutes.controllers.portal.Portal.setAnnotationVisibilityPost(id).ajax({
      data: data,
      success: function(data) {
        console.log("Set visibility to ", data)
      }
    });
  });
});