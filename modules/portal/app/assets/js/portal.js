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
});

