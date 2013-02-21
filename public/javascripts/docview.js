jQuery(function($) {

  // Chosen selects - makes multi-select pretty
  $(".chzn-select").chosen();

  // Delete inline date period tables
  $(".inline-element-set").on("click", ".remove-inline-element", function(event) {
    $(this).closest(".inline-element").remove();
    event.preventDefault();
  });

  $(".inline-element-set").on("click", ".add-inline-element", function(event) {
    var container = $(event.target).closest(".inline-element-set");
    var template = $(".inline-element-template", container);
    var idx = $(".inline-element", container).length;
    var elem = $(template.html().replace(/IDX/g, idx));
    container.append(elem);
    event.preventDefault();
  });
});