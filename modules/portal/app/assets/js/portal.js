jQuery(function ($) {

  // Validate any forms with 'validate-form' class...
  $(".validate-form").validate();
  $(document).ajaxComplete(function () {
    $(".validate-form").validate();
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

//  /**
//   * Handle watching/unwatching items using Ajax...
//   */
//  $(document).on("click", "a.watch-item", function (e) {
//    e.preventDefault();
//
//    var watcherFunc = jsRoutes.controllers.portal.Portal.watchItemPost,
//        unwatcherFunc = jsRoutes.controllers.portal.Portal.unwatchItemPost,
//        $elem = $(e.target),
//        id = $elem.data("item"),
//        watch = $elem.hasClass("watch");
//
//    var call, href, addcls, remcls;
//    if (watch) {
//      call = watcherFunc;
//      href = unwatcherFunc;
//      addcls = "unwatch";
//      remcls = "watch";
//    } else {
//      call = unwatcherFunc;
//      href = watcherFunc;
//      addcls = "watch";
//      remcls = "unwatch";
//    }
//
//    call(id).ajax({
//      beforeSend: function () {
//        $elem.addClass("spinner");
//      },
//      success: function (data) {
//        $elem
//            .removeClass(remcls)
//            .addClass(addcls)
//            .attr("href", href(id).url);
//      },
//      complete: function () {
//        $elem.removeClass("spinner");
//      }
//    });
//  });
});

