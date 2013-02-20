jQuery(function($) {

  // Chosen selects - makes multi-select pretty
  $(".chzn-select").chosen();

  // Delete inline date period tables
  var wrapper = $("#date-row-template");
  $(".remove-inline-date").click(function(event) {
    var elem = $(this).closest(".inline-date");
      $(".inline-date").length > 1 ? elem.remove() : elem.detach().appendTo(wrapper);
    event.preventDefault();
  });
});