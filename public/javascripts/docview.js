jQuery(function($) {

  // Click to expand additonal data sections
  $("table.unknown-data").hide();
  $(".expand-unknown-data").click(function(e) {
    $(this).closest(".unknown-data-section").find("table.unknown-data").toggle();
    console.log("click...")
    e.preventDefault();
  });

  // Chosen selects - makes multi-select pretty
  $(".chzn-select").chosen();

  // Fade success flash message after 3 seconds
  $(".success-pullup").fadeOut(3000);

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

	$('[data-toggle="tooltip"]').tooltip({placement: "right"});

});