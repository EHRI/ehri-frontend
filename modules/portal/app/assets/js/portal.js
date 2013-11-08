jQuery(function($) {

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
    $(".select2").select2(select2Opts);
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
    jsRoutes.controllers.portal.Portal.activityMore(offset).ajax({
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
        $("#submit-profile-update").click(function(e2) {
          e2.preventDefault();
          var url = jsRoutes.controllers.portal.Portal.updateProfilePost().url;
          var method = jsRoutes.controllers.portal.Portal.updateProfilePost().method;
          console.log($("#update-profile-form").serialize())
          $.ajax({
            url: url,
            type: method,
            data: $("#update-profile-form").serialize(),
            success: function(data) {
              $("#update-profile-form").remove();
              $("#profile-details").replaceWith(data).show();
            }
          })
        });

        $("#cancel-profile-update").click(function(e3) {
          e3.preventDefault();
          $("#profile-details").show();
          $("#update-profile-form").remove();
        });
      }
    });
  });
});

