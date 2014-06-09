// Handle slide-out suggestions form
//

jQuery(function($) {
  // handle suggestion form submission... this is a bit
  // gross and fragile.
  var $formContainer = $("#suggestions"),
      $cancel = $("a#cancel", $formContainer),
      $form = $("form#suggestion-form"),
      $submit = $("button[type='submit']", $form),
      $thanks = $(".alert-success", $formContainer),
      $text = $("textarea[name='text']", $form),
      $handle = $('.slide-out-div .handle');



  function feedbackOut(e) {
    if($(e.target).parents(".slide-out-div").length != 1 || $(e.target).hasClass("feedback-close") || $(e.target).parent().hasClass("feedback-close")) {
      $handle.trigger("click");
    }
  }
  $handle.on("click", function(e) {
    e.preventDefault();

    $formContainer.toggle(300, function() {
      if($formContainer.is(":visible")) {
        $form.append(
          $("<a />", {
            "class" : "feedback-close",
            "html" : "<span class='glyphicon glyphicon-remove'></span>"
          })
        );
        $(document).bind("click", feedbackOut);
      } else {
        $(document).unbind("click", feedbackOut);
        $formContainer.find(".feedback-close").remove();
      }
    });
  });

  $form.validate({
    showErrors: function(em, el) {}
  });

  
  $form.on("keyup", function(event) {
    $submit.prop("disabled", !$form.valid());
  });

  $(".modal-close", $formContainer).on("click", function() {
    $handle.trigger("click");
  });

  $cancel.on("click", function(e) {
    e.preventDefault();
    $text.val("");
    $handle.trigger("click");
  });

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