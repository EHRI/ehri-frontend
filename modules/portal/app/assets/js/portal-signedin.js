jQuery(function ($) {

  "use strict";

  /*
   Tooltip
   */
  $('.watch , .unwatch').tooltip({
    delay: {
      show: 600,
      hide: 100
    },
    container: 'body'
  });

  /**
   * Markdown helper
   */

    // Allow dl, dt, and dd elements in tooltips
  var tooltipWhitelist = $.fn.tooltip.Constructor.DEFAULTS.whiteList;
  tooltipWhitelist.dl = [];
  tooltipWhitelist.dt = [];
  tooltipWhitelist.dd = [];

  $(document).on("click", ".markdown textarea", function () {
    var $item = $(this);
    $item.parent().addClass("active").delay(2000).queue(function (next) {
      if ($(".popover .description-markdown-cheatsheet").length === 0) {
        $item.removeClass("active");
      }
      next();
    });
  });

  $(document).on("change", ".markdown textarea", function () {
    $(this).parent().removeClass("active");
  });
  $(document).on("keyup", ".markdown textarea", function () {
    $(this).parent().removeClass("active");
  });

  $(document).on("click", ".markdown .markdown-helper", function () {
    var $item = $(this);

    if (typeof $item.attr("data-popovered") === "undefined" || $item.attr("data-popovered") !== "true") {
      $item.popover({
        html: true,
        placement: "bottom",
        content: function () {
          return $(".markdown-cheatsheet").html();
        }
      });
      $item.attr("data-popovered", "true");
      $item.popover("show");

      $item.on('hidden.bs.popover', function () {
        $item.parents(".markdown").removeClass("active");
      });
    }
  });

  /**
   * Activity-related functions
   */

    // Fetch more activity...
  $(document).on("click", "a#activity-stream-fetchmore", function (event) {
    event.preventDefault();
    var $elem = $(event.target);
    var offset = parseInt($(event.target).attr("data-offset")) || 0;
    var limit = parseInt($(event.target).attr("data-limit")) || 20;
    var href = event.target.href;
    $.ajax({
      url: href,
      success: function (data, _, response) {
        var done = response.getResponseHeader("activity-more") !== 'true';
        $("#activity-stream").append(data);
        if (done) {
          $elem.hide();
        } else {
          console.log("Replace: ", (offset + limit), limit )
          $elem
            .attr("data-offset", (offset + limit))
            .attr("data-limit", limit)
            .attr("href",
              href.replace(/offset=\d+/, "offset=" + (offset + limit))
                  .replace(/limit=\d+/, "limit=" + limit));
        }
		  }
		});
	});

  /**
  * Function for changing icon loading
  * It should be called twice : before the ajax call and inside the success() to revert to the original icon
  * $elem = DOM element in jQuery format $(elem) on which the user click. Eg : In $(document).on("click", ".btn"), this would be $(this)
  * formerIcon = class of the icon that should be removed. Eg. : For .glyphicon.glyphicon-star this would be "glyphicon-star"
  */
  function changeGlyphToLoader($elem, formerIcon, iconClass) {
    var loadingClass = "glyphicon-refresh",
        spinningClass = " spin",
        iconClass = iconClass || "glyphicon";
    if ($elem.hasClass(iconClass)) {
      if ($elem.hasClass(formerIcon)) {
        $elem.removeClass(formerIcon).addClass(loadingClass + spinningClass);
      } else {
        $elem.removeClass(loadingClass + spinningClass).addClass(formerIcon);
      }
    } else {
      if ($elem.find("." + loadingClass).length === 1) {
        $elem.find("." + loadingClass).remove();
      } else {
        $elem.prepend('<span class="glyphicon ' + loadingClass + spinningClass + '"></span> ')
      }
    }
  }

  /**
	* Handler following/unfollowing users via Ajax.
	*/
  $(document).on("click", "a.follow, a.unfollow", function (e) {
    e.preventDefault();

    var url = this.href,
        followerListFunc = jsRoutes.controllers.portal.social.Social.followersForUser,
        followingListFunc = jsRoutes.controllers.portal.social.Social.followingForUser,
        $elem = $(this),
        id = $elem.data("item"),
        follow = $elem.hasClass("follow");

    var $other = follow
      ? $elem.parent().find("a.unfollow")
      : $elem.parent().find("a.follow");

    changeGlyphToLoader($elem);
    $.post(url, function() {
      // Swap the buttons and, if necessary, reload
      // their followers list...
      $elem.hide();
      changeGlyphToLoader($elem);
      $other.show();

      // Munge follower counts and lists
      // FIXME: Disabled since it's broken, see #494
      //if ($elem.parents(".user-list-item").size() === 0) {
      //  $(".browse-users-followers").load(followerListFunc(id).url);
      //  $(".browse-users-following").load(followingListFunc(id).url)
      //}
      //// If a follower count is shown, munge it...
      //var fc = $(".user-follower-count");
      //if (fc.size()) {
      //  var cnt = parseInt(fc.html(), 10);
      //  fc.html(follow ? (cnt + 1) : (cnt - 1));
      //}
    });
  });

  /**
   * Handle watching/unwatching items using Ajax...
   */
  $(document).on("click", "a.watch, a.unwatch", function (e) {
    e.preventDefault();

    var url = this.href,
        $elem = $(this),
        id = $elem.data("item"),
        watch = $elem.hasClass("watch");
    var call, $other, icon, $iconElem;

    if (watch) {
          $other = $elem.parent().find("a.unwatch"),
          icon = "glyphicon-star-empty";
    } else {
          icon = "glyphicon-star",
          $other = $elem.parent().find("a.watch");
    }

    if ($elem.hasClass("glyphicon")) {
      $iconElem = $elem;
    } else {
      $iconElem = $elem.find(".glyphicon")
    }

    changeGlyphToLoader($iconElem, icon);
    $.post(url, function () {
      // Swap the buttons and, if necessary, reload
      // their followers list...
      $elem.hide();
      changeGlyphToLoader($iconElem, icon);
      $other.show();

      // If a watch count is shown, munge it...
      var fc = $(".item-watch-count");
      if (fc.size()) {
        var cnt = parseInt(fc.html(), 10);
        fc.html(watch ? (cnt + 1) : (cnt - 1));
      }

      //If it is on profile page, remove the row
      if (watch === false) {
        if ($("#user-watch-list").length === 1) {
          var par = $("#" + id);
          par.hide(300, function () {
            par.remove();
          });
        }
      }
    });
  });

  $(document).on("click", "a.promotion-action", function (e) {
    e.preventDefault();
    var $item = $(this),
        url = this.href,
        $iconElem = $item.find(".fa"),
        icon = $item.hasClass("promote")
            ? "fa-caret-up"
            : "fa-caret-down";
    changeGlyphToLoader($iconElem, icon, "fa");
    $.post(url, function(data) {
      $item.closest("li").html(data);
      changeGlyphToLoader($iconElem, icon);
    })
  });

  /**
   * Bookmark items.
   */
  $(document).on("click", "a.bookmark-item", function (e) {
    e.preventDefault();
    var url = this.href,
        $elem = $(this),
        id = $elem.data("item");

    $.post(url, function () {
      $elem.addClass("bookmarked");
    });
  });

  /**
   * Annotation-related functions
   */

    // Hide annotate field links unless we hover the field...
  $(".item-text-field").hoverIntent(function () {
    $(".item-text-field").not(this).find(".annotate-field")
        .addClass("inactive");
    $(".annotate-field", this).removeClass("inactive");
  }, function () {
    $(".annotate-field", this).addClass("inactive");
  });


  // Show/hide hidden annotations...
  $(".show-other-annotations").click(function (event) {
    event.preventDefault();
    $(this).find("span")
        .toggleClass("glyphicon-chevron-up")
        .toggleClass("glyphicon-chevron-down")
        .end()
        .closest(".item-text-field-annotations, .description-annotations")
        .find(".other").toggle();
  });

  function insertAnnotationForm($elem, data, loaderContainer) {
    if (typeof loaderContainer !== "undefined") {
      loaderContainer.remove();
    }
    var $container = $elem.hasClass("annotate-field")
      ? $(".annotate-item-controls[data-target='" + $elem.attr("data-target") + "']")
      : $elem.parent();

    $elem.hide();
    $container.after(data);
    $container.next().find("textarea").focus();
    if ($.fn.select2) {
      $(data).find("select.custom-accessors").select2({
        placeholder: "Select a set of groups or users",
        width: "copy"
      });
    }
  }

  function insertAnnotationLoader($elem) {
    return EhriJs.$loader.appendTo($elem.parent().parent());
  }

  function showAnnotationControl($form) {
    var $annoItem = $form.prev().find(".annotate-item");
    var $annoBtn = $form.parents(".item-text-field").find(".annotate-field");

    if ($annoItem.length !== "undefined" && $annoItem.length === 1) {
      $annoItem.show();
    } else {
      $annoBtn.show();
    }
  }

  // Fields are very similar but we have to use the field as part
  // of the form submission url...
  $(document).on("click", "a.annotate-field, a.annotate-item", function (e) {
    e.preventDefault();
    var $elem = $(this),
        action = this.href;
    var loaderContainer = insertAnnotationLoader($elem);
    $.get(action, function (data) {
      insertAnnotationForm($elem, data, loaderContainer);
    });
  });


  // POST back an annotation form and then replace it with the returned
  // data.
  $(document).on("submit", ".create-annotation-form", function (e) {
    e.preventDefault();
    var $form = $(this);
    var action = $form.attr("action");
    $.post($form.attr("action"), $form.serialize(), function (data) {
      showAnnotationControl($form);
      $form.parents(".annotation-set").find(".annotation-list").append(data);
      $form.remove();
    });
  });

  // Fields are very similar but we have to use the field as part
  // of the form submission url...
  $(document).on("click", "a.edit-annotation", function (e) {
    e.preventDefault();
    var $elem = $(this),
        url = this.href,
        id = $elem.data("item");
    $.get(url, function (data) {
        //$elem.closest(".annotation").hide().after(data)
        var $data = $(data)
            .insertAfter($elem.closest(".annotation").hide());
        if ($.fn.select2) {
          $data.find("select.custom-accessors").select2({
            placeholder: "Select a set of groups or users",
            width: "copy"
          });
        }
    });
  });

  $(document).on("click", ".edit-annotation-form .close", function (e) {
    e.preventDefault();
    var $form = $(e.target).parents(".edit-annotation-form");
    var hasData = $("textarea[name='body']", $form).val().trim() !== "";
    if (!hasData || confirm("Discard changes?")) {
      $form.prev(".annotation").show();
      $form.remove();
    }
  });

  $(document).on("click", ".create-annotation-form .close", function (e) {
    e.preventDefault();
    var $form = $(e.target).parents(".create-annotation-form");
    var hasData = $("textarea[name='body']", $form).val().trim() !== "";
    if (!hasData || confirm("Discard comment?")) {
      showAnnotationControl($form);
      $form.remove();
    }
  });

  // POST back an annotation form and then replace it with the returned
  // data.
  $(document).on("submit", ".edit-annotation-form", function (e) {
    e.preventDefault();
    var $form = $(this),
        action = $form.attr("action");
    $.post(action, $form.serialize(), function (data) {
      $form.next(".annotate-field").show();
      $form.replaceWith(data);
    });
  });

  // Fields are very similar but we have to use the field as part
  // of the form submission url...
  $(document).on("click", "a.delete-annotation", function (e) {
    e.preventDefault();
    var $elem = $(this),
        check = $elem.attr("title"),
        action = this.href;
    if (confirm(check + "?")) {
      var $ann = $elem.closest(".annotation");
      $ann.hide();
      $.post(action, function (data) {
        $ann.remove();
      }).fail(function () {
        $ann.show();
      });
    }
  });

  $(document).on("click", ".promote-annotation, .demote-annotation", function (e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item"),
        check = $elem.attr("title"),
        action = this.href;
    if (confirm(check + "?")) {
      var $ann = $elem.closest(".annotation");
      $.post(action, function (data) {
        $ann.replaceWith(data);
      });
    }
  });

  // Handling of custom visibility selector.
  $(document).on("change", "input[type=radio].visibility", function (e) {
    var $customVis = $(".custom-visibility");
    $customVis
        .toggle(e.target.value === "custom");
    if ($.fn.select2) {
      $customVis.find("select.custom-accessors").select2({
        placeholder: "Select a set of groups or users",
        width: "copy"
      });
    }
  });

  // Set visibility of annotations
  // POST back an annotation form and then replace it with the returned
  // data.
  $(document).on("change", ".edit-annotation-form .visibility, .edit-annotation-form .custom-accessors", function (e) {
    e.preventDefault();
    // Toggle the accessors list
    var $form = $(this).closest("form"),
        id = $form.prev(".annotation").attr("id"),
        data = $form.serialize();
    jsRoutes.controllers.portal.annotate.Annotations.setAnnotationVisibilityPost(id).ajax({
      data: data,
      success: function (data) {
        console.log("Set visibility to ", data);
      }
    });
  });


  /**
   * Messaging
   */
  $("body").on("submit", ".message-form", function (e) {
    var $form = $(this);
    e.preventDefault();
    $.post($form.attr("action"), $form.serialize()).done(function (data) {
      EhriJs.alertSuccess(data.ok);
      $form.closest(".modal").modal("hide");
      $form.find("#subject,#message").val("");
    });
  });
});
