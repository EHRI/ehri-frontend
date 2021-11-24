import {shallowMount} from '@vue/test-utils';
import ManagerHarvest from './_manager-harvest.vue';
import {DatasetManagerApi} from "../api";

jest.mock('../api');

describe('ManagerHarvest component', () => {
  let api = new DatasetManagerApi({}, 'r1');
  const wrapper = shallowMount(ManagerHarvest, {
    propsData: {
      fileStage: 'input',
      config: {},
      api: api,
    }
  });

  test('mounting', () => {
    expect(wrapper.isVisible()).toBeTruthy();
    expect(wrapper.find("#harvest-panel-container .panel-placeholder").exists()).toBe(true);
  });
})
