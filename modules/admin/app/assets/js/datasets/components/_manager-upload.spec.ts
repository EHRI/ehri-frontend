import {shallowMount} from '@vue/test-utils';
import ManagerUpload from './_manager-upload.vue';
import {DatasetManagerApi} from "../api";
import {ImportDataset} from "../types";

jest.mock('../api');

describe('ManagerUpload component', () => {
  let api = new DatasetManagerApi({}, 'r1');
  const wrapper = shallowMount(ManagerUpload, {
    props: {
      dataset: {
        id: 'test',
        src: 'upload'
      } as ImportDataset,
      fileStage: 'input',
      config: {},
      api: api,
    },
  });

  test('mounting', () => {
    expect(wrapper.isVisible()).toBeTruthy();
    expect(wrapper.find("#upload-panel-container .panel-placeholder").exists()).toBe(true);
  });
})
