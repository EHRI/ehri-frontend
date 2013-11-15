jQuery(function($) {

  // Validate any forms with 'validate-form' class...
  $(".validate-form").validate();
  $(document).ajaxComplete(function() {
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
  $(document).ajaxComplete(function() {
    //$(".select2").select2(select2Opts);
  });

  $(".select2").select2(select2Opts).change(function(e) {
    if ($(e.target).hasClass("autosubmit")) {
      $(e.target).closest("form").submit();
    }
  });

  $(".facet-toggle").change(function(e) {
    $(e.target).closest("form").submit();
  });

  // Fetch more activity...
  $("#activity-stream-fetchmore").click(function(event) {
    var offset = $(event.target).data("offset");
    var limit = $(event.target).data("limit")
    jsRoutes.controllers.portal.Portal.personalisedActivityMore(offset).ajax({
      success: function(data) {
          console.log("Data", data);
          $("#activity-stream").append(data);
          $(event.target).data("offset", offset + limit);
      }
    });
  });


  // Editing profile
  $(document).on("click", "#edit-profile", function(e) {
    e.preventDefault();
    jsRoutes.controllers.portal.Portal.updateProfile().ajax({
      success: function(data) {
        $("#profile-details").hide().after(data);
        $("#update-profile-form").submit(function(submitEvent) {
          submitEvent.preventDefault();
          var formData = $(submitEvent.target).serialize();
          if ($(submitEvent.target).valid()) {
            var url = jsRoutes.controllers.portal.Portal.updateProfilePost().url;
            var method = jsRoutes.controllers.portal.Portal.updateProfilePost().method;
            $.ajax({
              url: url,
              type: method,
              data: formData,
              success: function(data) {
                $("#update-profile-form").remove();
                $("#profile-details").replaceWith(data).show();
              }
            });
          }
        });

        $("#cancel-profile-update").click(function(cancelEvent) {
          cancelEvent.preventDefault();
          $("#update-profile-form").remove();
          $("#profile-details").show();
        });
      }
    });
  });

  $(document).on("click", ".user-list-item .follow a", function(e) {
    e.preventDefault();
    $.post(e.target.href, "", function(data) {
      $(e.target).parents(".user-list-item").find(".follow").hide();
      $(e.target).parents(".user-list-item").find(".unfollow").show();
    })
  });

  $(document).on("click", ".user-list-item .unfollow a", function(e) {
    e.preventDefault();
    $.post(e.target.href, "", function(data) {
      $(e.target).parents(".user-list-item").find(".unfollow").hide();
      $(e.target).parents(".user-list-item").find(".follow").show();
    })
  });

    var watcherFunc = jsRoutes.controllers.portal.Portal.watchItemPost,
        unwatcherFunc = jsRoutes.controllers.portal.Portal.unwatchItemPost;

    $(document).on("click", "a.watch-item", function(e) {
        e.preventDefault();
        var $elem = $(e.target);
        var id = $elem.data("item");
        var watch = $elem.hasClass("watch");

        var call, href, addcls, remcls;
        if (watch) {
          call = watcherFunc;
          href = unwatcherFunc;
          addcls = "unwatch";
          remcls = "watch";
        } else {
            call = unwatcherFunc;
            href = watcherFunc;
            addcls = "watch";
            remcls = "unwatch";
        }

        call(id).ajax({
          beforeSend: function() {
            $elem.addClass("spinner");
          },
          success: function(data) {
              $elem
                  .removeClass(remcls)
                  .addClass(addcls)
                  .attr("href", href(id).url);
          },
          complete: function() {
            $elem.removeClass("spinner");
          }
        });
    });
});

