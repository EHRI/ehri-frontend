jQuery(function($) {

  $(".select2").select2({
    placeholder: "Select an option...",
    allowClear: true,
    dropdownAutoWidth: true,
    dropdownCssClass: "facet-select-dropdown",
    minimumInputLength: 0
  }).change(function(e) {
        $(e.target).closest("form").submit();
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
});

