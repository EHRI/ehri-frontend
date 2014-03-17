// Handle slide-out suggestions form
//

jQuery(function($) {
  $('.slide-out-div .handle').on("click", function(e) {
    e.preventDefault();
    that = $(this);
    sugg = that.parents(".slide-out-div").find("#suggestions");
    sugg.toggle(300);
  });
  // handle suggestion form submission... this is a bit
  // gross and fragile.
  var $formContainer = $("#suggestions"),
      $cancel = $("a#cancel", $formContainer),
      $form = $("form#suggestion-form")
      $submit = $("button[type='submit']", $form),
      $thanks = $(".alert-success", $formContainer),
      $text = $("textarea[name='text']", $form);

  $form.validate({
    showErrors: function(em, el) {}
  })

  $form.on("keyup", function(event) {
    $submit.prop("disabled", !$form.valid());
  });

  $(".modal-close", $formContainer).on("click", function() {
    $(".slide-out-div > .handle").trigger("click");
  });

  $cancel.on("click", function(e) {
    e.preventDefault();
    $text.val("")
    $(".slide-out-div > .handle").trigger("click");
  })

  $submit.on("click", function(event) {
    event.preventDefault();
    $submit.prop("disabled", true);
    $.post($form.attr("action"), $form.serialize(), function(data, textStatus) {

      /* <-- UI for Thanks */

      $form.hide(100, function() {
        $thanks.slideDown(500, function() {
          setTimeout(function() {          
            $text.val("");
      
            $thanks.slideUp(500, function() {
              $(".slide-out-div > .handle").trigger("click").queue(function(next) {
                $formContainer.find("form").show();
                next();
              });

            });
          }, 1000);
        });
      });

      /* --> UI for Thanks */
    
    });
  });
});