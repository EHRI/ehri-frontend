import {shallowMount} from '@vue/test-utils';
import ManagerRs from './_manager-rs.vue';
import {DatasetManagerApi} from "../api";

jest.mock('../api');

describe('ManagerRs component', () => {
  let api = new DatasetManagerApi({}, 'r1');
  const wrapper = shallowMount(ManagerRs, {
    propsData: {
      fileStage: 'input',
      config: {},
      api: api,
    }
  });

  test('mounting', () => {
    expect(wrapper.isVisible()).toBeTruthy();
    expect(wrapper.find("#rs-panel-container .panel-placeholder").exists()).toBe(true);
  });
})
