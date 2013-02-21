jQuery(function($) {

  // Chosen selects - makes multi-select pretty
  $(".chzn-select").chosen();

  // Delete inline date period tables
  $(".inline-date-set").on("click", ".remove-inline-date", function(event) {
    $(this).closest(".inline-date").remove();
    event.preventDefault();
  });

  $(".inline-date-set").on("click", ".add-inline-date", function(event) {
    var container = $(event.target).closest(".inline-date-set");
    var template = $(".inline-date-template", container);
    var idx = $(".inline-date", container).length;
    var elem = $(template.html().replace(/IDX/g, idx));
    container.append(elem);
    event.preventDefault();
  });
});