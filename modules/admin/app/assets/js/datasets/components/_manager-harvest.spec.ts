import {mount} from '@vue/test-utils';
import ManagerHarvest from './_manager-harvest.vue';
import {DatasetManagerApi} from "../api";
import {ImportDataset} from "../types";

jest.mock('../api');

/**
 * @jest-environment jsdom
 */
describe('ManagerHarvest component', () => {
  let api = new DatasetManagerApi({}, 'r1');
  const wrapper = mount(ManagerHarvest, {
    shallow: true,
    props: {
      dataset: {
        id: 'test',
        src: 'oaipmh'
      } as ImportDataset,
      fileStage: 'input',
      config: {},
      api: api,
    },
  });

  test('mounting', () => {
    expect(wrapper.isVisible()).toBeTruthy();
    expect(wrapper.find("#harvest-panel-container .panel-placeholder").exists()).toBe(true);
  });
})
