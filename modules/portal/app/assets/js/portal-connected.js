jQuery(function ($) {
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
   *  Profile page
   */

  $('.user-profile-sidebar a[data-toggle="tab"]').on('shown.bs.tab', function (e) {
    // e.target // activated tab
    // e.relatedTarget // previous tab
    var t = $(e.target).data("tabidx"); // chop off #
    $(".tab-header h3").removeClass("active");
    $(".tab-header h3 a[data-tabidx='" + t + "']")
        .parent()
        .addClass("active");
  });

  /**
   * Markdown helper
   */

  $(document).on("click", ".markdown textarea", function () {
    $(this).parent().addClass("active").delay(2000).queue(function (next) {
      if ($(".popover .description-markdown-cheatsheet").length === 0) {
        $(this).removeClass("active");
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
    that = $(this);

    if (typeof that.attr("data-popovered") === "undefined" || that.attr("data-popovered") !== "true") {
      that.popover({
        html: true,
        placement: "bottom",
        content: function () {
          return $(".markdown-cheatsheet").html();
        }
      });
      that.attr("data-popovered", "true");
      that.popover("show");

      that.on('hidden.bs.popover', function () {
        that.parents(".markdown").removeClass("active");
      });
    }
  });

  /**
   * Activity-related functions
   */

    // Fetch more activity...
  $("#activity-stream-fetchmore").click(function (event) {
    var $elem = $(event.target);
    var page = $(event.target).data("page");
    var count = $(event.target).data("count");
    jsRoutes.controllers.portal.Social.personalisedActivity(page, count).ajax({
      success: function (data, _, response) {
        var done = response.getResponseHeader("activity-more") != 'true';
        $("#activity-stream").append(data);
        if (done) {
          $elem.hide();
        } else {
          $elem.data("page", page + 1);
        }
		  }
		});
	});

	/**
	* Handler following/unfollowing users via Ajax.
	*/
	$(document).on("click", "a.follow, a.unfollow", function (e) {
		e.preventDefault();

		var followFunc = jsRoutes.controllers.portal.Social.followUserPost,
		    unfollowFunc = jsRoutes.controllers.portal.Social.unfollowUserPost,
		    followerListFunc = jsRoutes.controllers.portal.Social.followersForUser,
		    $elem = $(this),
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
        if($elem.parents(".user-list-item").size() === 0) {
          $(".browse-users-followers").load(followerListFunc(id).url);
        }
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

		var watchFunc = jsRoutes.controllers.portal.Profile.watchItemPost,
		    unwatchFunc = jsRoutes.controllers.portal.Profile.unwatchItemPost,
		    $elem = $(this),
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

        //If it is on profile page, remove the row
        if (watch === false) {
          if ($("#user-watch-list").length == 1) {
            var par = $("#" + id);
            par.hide(300, function () {
              par.remove();
            });
          }
        }
      }
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
    $(data).find("select.custom-accessors").select2({
      placeholder: "Select a set of groups or users",
      width: "copy"
    });
  }

  function insertAnnotationLoader($elem) {
    loaderContainer = $loader.appendTo($elem.parent().parent());
    return loaderContainer;
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

  // Load an annotation form...
  $(document).on("click", ".annotate-item", function (e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item"),
        did = $elem.data("did");
    jsRoutes.controllers.portal.Annotations.annotate(id, did).ajax({
      success: function (data) {
        insertAnnotationForm($elem, data);
      }
    });
  });

  // Fields are very similar but we have to use the field as part
  // of the form submission url...
  $(document).on("click", ".annotate-field", function (e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item"),
        did = $elem.data("did"),
        field = $elem.data("field");
    loaderContainer = insertAnnotationLoader($elem);
    jsRoutes.controllers.portal.Annotations.annotateField(id, did, field).ajax({
      success: function (data) {
        insertAnnotationForm($elem, data, loaderContainer);
      }
    });
  });


  // POST back an annotation form and then replace it with the returned
  // data.
  $(document).on("submit", ".annotate-item-form", function (e) {
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
  $(document).on("click", ".edit-annotation", function (e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item");
    jsRoutes.controllers.portal.Annotations.editAnnotation(id).ajax({
      success: function (data) {
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

  $(document).on("click", ".edit-annotation-form .close", function (e) {
    e.preventDefault();
    var $form = $(e.target).parents(".edit-annotation-form");
    var hasData = $("textarea[name='body']", $form).val().trim() !== "";
    if (!hasData || confirm("Discard comment?")) {
      $form.prev(".annotation").show();
      $form.remove();
    }
  });

  $(document).on("click", ".annotate-item-form .close", function (e) {
    e.preventDefault();
    var $form = $(e.target).parents(".annotate-item-form");
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
    var $form = $(this);
    var action = $form.closest("form").attr("action");
    if ($form.parents(".description-annotations") !== "undefined" && $form.parents(".description-annotations").length >= 1) {
      action += "?isField=false";
    }
    $.post(action, $form.serialize(), function (data) {
      $form.next(".annotate-field").show();
      $form.replaceWith(data);
    });
  });

  // Fields are very similar but we have to use the field as part
  // of the form submission url...
  $(document).on("click", ".delete-annotation", function (e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item"),
        check = $elem.attr("title");
    if (confirm(check + "?")) {
      var $ann = $elem.closest(".annotation");
      $ann.hide();
      jsRoutes.controllers.portal.Annotations.deleteAnnotationPost(id).ajax({
        success: function () {
          $ann.remove();
        },
        error: function () {
          $ann.show();
        }
      });
    }
  });

  // Handling of custom visibility selector.
  $(document).on("change", "input[type=radio].visibility", function (e) {
    $(".custom-visibility")
        .toggle(e.target.value === "custom")
        .find("select.custom-accessors").select2({
          placeholder: "Select a set of groups or users",
          width: "copy"
        });
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
    jsRoutes.controllers.portal.Annotations.setAnnotationVisibilityPost(id).ajax({
      data: data,
      success: function (data) {
        console.log("Set visibility to ", data);
      }
    });
  });


  /**
   * Messaging
   */
  /*
   *   History
   */
  $("body").on("submit", ".message-form", function (e) {
    var $form = $(this);
    e.preventDefault();
    $.post($form.attr("action"), $form.serialize())
        .done(function (data) {
          EhriJs.alertSuccess(data.ok);
          $form.closest(".modal").modal("hide");
        }).fail(function () {
          // TODO: Figure out what to do here...
        });
  });
});