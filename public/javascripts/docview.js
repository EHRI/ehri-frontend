jQuery(function($) {

  // Chosen selects - makes multi-select pretty
  $(".chzn-select").chosen();

  // Delete inline date period tables
  $(".remove-inline-date").click(function(event) {
    $(this).closest(".inline-date").remove();
  });
});