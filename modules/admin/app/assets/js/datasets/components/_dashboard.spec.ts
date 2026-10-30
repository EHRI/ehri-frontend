import {mount, flushPromises, VueWrapper} from '@vue/test-utils';
import Dashboard from './_dashboard.vue';
import {DashboardApi} from '../dashboard-api';

describe('Dashboard component', () => {
  const info = (repoId: string, name: string, countryCode?: string, countryName?: string) => ({
    repoId, name, altNames: "", logoUrl: "", sets: [{}], countryCode, countryName,
  });

  const api = Object.assign(Object.create(DashboardApi.prototype), {
    listAllDatasets: () => Promise.resolve([
      info("nl-1", "Zeeland Archive", "nl", "Netherlands"),
      info("de-1", "Berlin Archive", "de", "Germany"),
      info("nl-2", "Amsterdam Archive", "nl", "Netherlands"),
      info("xx-1", "Nowhere Archive"),
    ]),
    managerUrl: () => "",
  });

  const mountDashboard = async () => {
    const wrapper = mount(Dashboard, {props: {config: {title: "Datasets"}, api}});
    await flushPromises();
    return wrapper;
  };

  const headings = (wrapper: VueWrapper<any>) => wrapper.findAll('.dashboard-country-heading')
      .map(h => h.text().replace(/\s*\d+$/, ''));

  test('groups institutions by country, with no country last', async () => {
    const wrapper = await mountDashboard();
    expect(headings(wrapper)).toEqual(["Germany", "Netherlands", "Other"]);
    const nl = wrapper.findAll('.dashboard-country')[1];
    expect(nl.findAll('.item-heading').map(h => h.text())).toEqual(["Zeeland Archive", "Amsterdam Archive"]);
  });

  test('filters by country name', async () => {
    const wrapper = await mountDashboard();
    await wrapper.find('#opt-filter').setValue('nether');
    expect(headings(wrapper)).toEqual(["Netherlands"]);
    expect(wrapper.findAll('.dashboard-institution-item').length).toBe(2);
  });
});
