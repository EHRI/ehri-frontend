/*
*	Add Params to research_guide_page
*/
ALTER TABLE `docview`.`research_guide_page` ADD COLUMN `params` VARCHAR(255) NULL DEFAULT NULL  AFTER `position`;
