jQuery(function ($) {

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


  // Editing profile
  $(document).on("click", "#edit-profile", function (e) {
    e.preventDefault();
    jsRoutes.controllers.portal.Portal.updateProfile().ajax({
      success: function (data) {
        $("#profile-details").hide().after(data);
        $("#update-profile-form").submit(function (submitEvent) {
          submitEvent.preventDefault();
          var formData = $(submitEvent.target).serialize();
          if ($(submitEvent.target).valid()) {
            var url = jsRoutes.controllers.portal.Portal.updateProfilePost().url;
            var method = jsRoutes.controllers.portal.Portal.updateProfilePost().method;
            $.ajax({
              url: url,
              type: method,
              data: formData,
              success: function (data) {
                $("#update-profile-form").remove();
                $("#profile-details").replaceWith(data).show();
              }
            });
          }
        });

        $("#cancel-profile-update").click(function (cancelEvent) {
          cancelEvent.preventDefault();
          $("#update-profile-form").remove();
          $("#profile-details").show();
        });
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

  // Load an annotation form...
  $(document).on("click", ".annotate-item", function(e) {
    e.preventDefault();
    var $elem = $(this),
        id = $elem.data("item"),
        did = $elem.data("did");
    jsRoutes.controllers.portal.Portal.annotate(id, did).ajax({
      success: function(data) {
        $elem.before(data).hide();
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
      success: function(data) {
        $elem.before(data).hide();
        $(data).find("select.custom-accessors").select2({
          placeholder: "Select a set of groups or users",
          width: "copy"
        });
      }
    });
  });

  $(document).on("click", ".annotate-item-form .close", function(e) {
    e.preventDefault();
    var $form = $(e.target).parents(".annotate-item-form");
    $form.next(".annotate-field, .annotate-item").show();
    $form.remove();
  });



  // POST back an annotation form and then replace it with the returned
  // data.
  $(document).on("submit", ".annotate-item-form, .annotate-field-form", function(e) {
    e.preventDefault();
    var $form = $(this);
    var action = $form.closest("form").attr("action");
    $.ajax({
      url: action,
      data: $form.serialize(),
      method: "POST",
      success: function(data) {
        $form.next(".annotate-field, .annotate-item").show()
        $form.replaceWith(data);
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
    $form.prev(".annotation").show();
    $form.remove()
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

